package com.shashi.rca.service;

import com.shashi.rca.dto.RcaContext;
import com.shashi.rca.dto.RcaReport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class LlmClient {

    private static final String SYSTEM_PROMPT = """
            You are an experienced Site Reliability Engineer.
            Your objective is to identify the root cause of production incidents across a microservices mesh.

            [UPSTREAM CALL PATHS] services that call the failing service (they may send it bad data or too much load):
            {upstream}

            [DOWNSTREAM CALL PATHS] services the failing service depends on (if they are down, it fails too):
            {downstream}

            [VERIFIED PAST INCIDENTS] human-verified root causes of similar incidents:
            {history}

            [INSTRUCTIONS]
            - Reason about how the error could propagate along the call paths above.
            - If the log indicates deserialization or schema issues, check whether an upstream producer changed its schema.
            - Only rely on past incidents if they really match; say so if there is no good match.
            """;

    private static final String USER_PROMPT = """
            ANOMALY ENCOUNTERED:
            Failing service: {service}
            Exception: {exception}
            Log message: {log}
            """;

    private final ChatClient chatClient;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final MeterRegistry meterRegistry;

    public LlmClient(ChatClient.Builder chatClientBuilder,
                     MeterRegistry meterRegistry,
                     @Value("${rca.llm.timeout:60s}") Duration timeout) {
        this.chatClient = chatClientBuilder.build();
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = CircuitBreaker.of("gemini", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build());
        this.timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(timeout).build());
        log.info("LLM: Gemini only (timeout={})", timeout);
    }

    public RcaReport analyze(RcaContext context) {
        log.info("[RCA step 4/4] Calling Gemini for structured RCA (service={}, exception={})",
                context.service(), context.exception());
        try {
            Callable<RcaReport> callGemini = () -> timeLimiter.executeFutureSupplier(
                    () -> CompletableFuture.supplyAsync(() -> invokeGemini(context)));
            return circuitBreaker.executeCallable(callGemini);
        } catch (CallNotPermittedException e) {
            meterRegistry.counter("rca.llm.failures", "provider", "gemini").increment();
            RuntimeException wrapped = new RuntimeException("Gemini circuit breaker is OPEN after repeated failures", e);
            log.error("[RCA STOPPED at Gemini] {}", LlmFailureMessages.summarize(wrapped));
            throw wrapped;
        } catch (Exception e) {
            meterRegistry.counter("rca.llm.failures", "provider", "gemini").increment();
            RuntimeException wrapped = new RuntimeException("Gemini call failed", e);
            log.error("[RCA STOPPED at Gemini] {}", LlmFailureMessages.summarize(wrapped));
            throw wrapped;
        }
    }

    private RcaReport invokeGemini(RcaContext context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return chatClient.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT).params(Map.of(
                            "upstream", context.upstreamPaths(),
                            "downstream", context.downstreamPaths(),
                            "history", context.similarIncidents())))
                    .user(u -> u.text(USER_PROMPT).params(Map.of(
                            "service", context.service(),
                            "exception", context.exception(),
                            "log", context.message() != null ? context.message() : "")))
                    .call()
                    .entity(RcaReport.class);
        } finally {
            sample.stop(Timer.builder("rca.llm.latency")
                    .tag("provider", "gemini")
                    .publishPercentiles(0.95)
                    .register(meterRegistry));
        }
    }
}
