package com.shashi.rca;

import com.shashi.rca.config.KafkaConfig;
import com.shashi.rca.dto.RcaReport;
import com.shashi.rca.model.Incident;
import com.shashi.rca.model.IncidentStatus;
import com.shashi.rca.repository.IncidentRepository;
import com.shashi.rca.repository.RcaReportRepository;
import com.shashi.rca.service.LlmClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real Kafka, Postgres/pgvector and Neo4j in Docker; the LLM and the embedding model are fakes,
 * so every run gives the same result. Skipped automatically when Docker is not available.
 */
@SpringBootTest(properties = {
        "spring.ai.google.genai.api-key=test-key-not-used-llm-is-mocked",
        "rca.kafka.retry.max-retries=1",
        "rca.kafka.retry.initial-interval=100ms"
})
@Testcontainers(disabledWithoutDocker = true)
class RcaPipelineIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.5.0");

    @Container
    @ServiceConnection
    static Neo4jContainer neo4j = new Neo4jContainer("neo4j:5.12.0-community");

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @MockitoBean
    LlmClient llmClient;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    RcaReportRepository rcaReportRepository;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void happyPathCreatesDoneIncidentWithReport() {
        when(llmClient.analyze(any())).thenReturn(sampleReport());
        String traceId = newTraceId();

        send(traceId, "account-service", "SerializationException", "Schema registry mismatch ID " + UUID.randomUUID());

        Incident incident = awaitStatus(traceId, IncidentStatus.DONE);
        assertThat(rcaReportRepository.findByIncident_Id(incident.getId())).isPresent();
        verify(llmClient, times(1)).analyze(any());
    }

    @Test
    void duplicateTraceIdIsProcessedOnce() {
        when(llmClient.analyze(any())).thenReturn(sampleReport());
        String traceId = newTraceId();
        String message = "Duplicate test " + UUID.randomUUID();

        send(traceId, "signup-service", "IllegalStateException", message);
        send(traceId, "signup-service", "IllegalStateException", message);
        // The raw topic has one partition, so once this marker is stored both copies above have been handled
        String markerTrace = newTraceId();
        send(markerTrace, "kyc-service", "MarkerException", "marker " + UUID.randomUUID());

        awaitStatus(traceId, IncidentStatus.DONE);
        awaitStatus(markerTrace, IncidentStatus.DONE);
        verify(llmClient, times(2)).analyze(any()); // once for the trace, once for the marker
    }

    @Test
    void llmFailureEndsInFailedStatusViaDeadLetterTopic() {
        when(llmClient.analyze(any())).thenThrow(new RuntimeException("LLM is down"));
        double dltBefore = dltCount();
        String traceId = newTraceId();

        send(traceId, "account-service", "PSQLException", "Connection slots exhausted " + UUID.randomUUID());

        // FAILED is only set by the listener on the DLT, so this proves the message reached it
        awaitStatus(traceId, IncidentStatus.FAILED);
        assertThat(dltCount()).isGreaterThan(dltBefore);
        verify(llmClient, times(2)).analyze(any()); // first attempt + 1 retry
    }

    @Test
    void repeatedErrorReusesReportInsteadOfCallingLlm() {
        when(llmClient.analyze(any())).thenReturn(sampleReport());
        String firstTrace = newTraceId();
        String secondTrace = newTraceId();
        String unique = "grouping-" + UUID.randomUUID().toString().substring(0, 8).replaceAll("\\d", "x");

        send(firstTrace, "kyc-service", "SocketTimeoutException", unique + " read timed out after 5012 ms for " + UUID.randomUUID());
        send(secondTrace, "kyc-service", "SocketTimeoutException", unique + " read timed out after 870 ms for " + UUID.randomUUID());

        Incident first = awaitStatus(firstTrace, IncidentStatus.DONE);
        Incident second = awaitStatus(secondTrace, IncidentStatus.DONE);
        assertThat(second.getDuplicateOf()).isEqualTo(first.getId());
        verify(llmClient, times(1)).analyze(any());
    }

    private void send(String traceId, String service, String exception, String message) {
        String json = """
                {"service":"%s","timestamp":"2026-09-30T10:00:00","traceId":"%s","exception":"%s","message":"%s"}
                """.formatted(service, traceId, exception, message).trim();
        kafkaTemplate.send(KafkaConfig.RAW_TOPIC, traceId, json).join();
    }

    private Incident awaitStatus(String traceId, IncidentStatus status) {
        return await().atMost(TIMEOUT).until(
                () -> incidentRepository.findByTraceId(traceId).orElse(null),
                incident -> incident != null && incident.getStatus() == status);
    }

    private double dltCount() {
        var counter = meterRegistry.find("rca.dlt.messages").tag("topic", KafkaConfig.ANALYSIS_DLT).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String newTraceId() {
        return "it-" + UUID.randomUUID();
    }

    private static RcaReport sampleReport() {
        return new RcaReport("Fake root cause for tests", 80, List.of("account-service"), List.of("Fake fix"));
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {

        /**
         * Same text -> same 1536-dimension vector, with no Gemini embedding API needed in tests.
         */
        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    List<Embedding> embeddings = new ArrayList<>();
                    for (int i = 0; i < request.getInstructions().size(); i++) {
                        embeddings.add(new Embedding(vector(request.getInstructions().get(i)), i));
                    }
                    return new EmbeddingResponse(embeddings);
                }

                @Override
                public float[] embed(Document document) {
                    return vector(document.getText());
                }

                @Override
                public int dimensions() {
                    return 1536;
                }
            };
        }

        private static float[] vector(String text) {
            Random random = new Random(text.hashCode());
            float[] v = new float[1536];
            for (int i = 0; i < v.length; i++) {
                v[i] = random.nextFloat();
            }
            return v;
        }
    }
}
