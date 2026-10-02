package com.shashi.rca.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmFailureMessagesTest {

    @Test
    void classifiesInvalidApiKey() {
        assertThat(LlmFailureMessages.categorize(new RuntimeException("HTTP 403 API key not valid")))
                .isEqualTo(LlmFailureMessages.Category.INVALID_API_KEY);
    }

    @Test
    void classifiesHighDemand() {
        assertThat(LlmFailureMessages.categorize(new RuntimeException("Gemini models are currently facing high demand")))
                .isEqualTo(LlmFailureMessages.Category.RATE_LIMIT_OR_OVERLOAD);
    }
}
