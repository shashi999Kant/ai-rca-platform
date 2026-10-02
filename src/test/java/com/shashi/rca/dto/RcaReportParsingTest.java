package com.shashi.rca.dto;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The LLM's wording changes every run, but the JSON shape must not. This checks that a saved, realistic
 * response still maps onto RcaReport the same way ChatClient.entity(RcaReport.class) does.
 */
class RcaReportParsingTest {

    private final BeanOutputConverter<RcaReport> converter = new BeanOutputConverter<>(RcaReport.class);

    @Test
    void parsesSavedLlmResponse() {
        String savedResponse = """
                ```json
                {
                  "rootCause": "signup-service started publishing AccountCreated as plain JSON while account-service expects Avro with a schema registry header.",
                  "confidence": 85,
                  "affectedServices": ["signup-service", "account-service"],
                  "recommendations": ["Roll back the signup-service serializer change", "Add a schema compatibility check to CI"]
                }
                ```
                """;

        RcaReport report = converter.convert(savedResponse);

        assertThat(report.rootCause()).contains("signup-service");
        assertThat(report.confidence()).isBetween(0, 100);
        assertThat(report.affectedServices()).containsExactly("signup-service", "account-service");
        assertThat(report.recommendations()).hasSize(2);
    }

    @Test
    void formatInstructionsMentionEveryField() {
        assertThat(converter.getFormat())
                .contains("rootCause", "confidence", "affectedServices", "recommendations");
    }
}
