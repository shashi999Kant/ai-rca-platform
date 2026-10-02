package com.shashi.rca.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorSignatureTest {

    @Test
    void normalizeReplacesVariablePartsWithPlaceholders() {
        String normalized = ErrorSignature.normalize(
                "Timeout after 5012 ms for request 3F2A9C1B-1234-4ABC-9DEF-0123456789AB at 2026-09-30T10:15:00Z, magic byte 0x7b");

        assertThat(normalized).isEqualTo("timeout after <n> ms for request <uuid> at <ts>, magic byte <hex>");
    }

    @Test
    void sameErrorWithDifferentIdsHasSameSignature() {
        String first = ErrorSignature.of("signup-service", "TimeoutException",
                "Call to account-service timed out after 5012 ms for request 3f2a9c1b-1234-4abc-9def-0123456789ab");
        String second = ErrorSignature.of("signup-service", "TimeoutException",
                "Call to account-service   timed out after 870 ms for request 9c1b3f2a-5678-4abc-9def-ba9876543210");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void differentServiceOrExceptionGivesDifferentSignature() {
        String message = "Call timed out after 100 ms";

        assertThat(ErrorSignature.of("signup-service", "TimeoutException", message))
                .isNotEqualTo(ErrorSignature.of("kyc-service", "TimeoutException", message))
                .isNotEqualTo(ErrorSignature.of("signup-service", "SocketTimeoutException", message));
    }

    @Test
    void nullMessageIsHandled() {
        assertThat(ErrorSignature.normalize(null)).isEmpty();
        assertThat(ErrorSignature.of("a", "B", null)).hasSize(64);
    }
}
