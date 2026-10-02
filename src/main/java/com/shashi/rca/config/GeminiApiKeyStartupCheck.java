package com.shashi.rca.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class GeminiApiKeyStartupCheck implements ApplicationRunner {

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(apiKey)) {
            log.error("""
                    [STARTUP] SPRING_AI_GOOGLE_GENAI_API_KEY is missing or empty.
                    RCA will fail at step 4 (Gemini). Set the variable in the same terminal before spring-boot:run.
                    """);
            return;
        }
        if (apiKey.length() < 20) {
            log.warn("[STARTUP] Gemini API key looks too short — check for typos.");
        } else {
            log.info("[STARTUP] Gemini API key is configured (length={}).", apiKey.length());
        }
    }
}
