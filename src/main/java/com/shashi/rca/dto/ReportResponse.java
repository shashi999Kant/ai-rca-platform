package com.shashi.rca.dto;

import com.shashi.rca.model.RcaReportEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record ReportResponse(
        String reportId,
        String incidentId,
        String rootCause,
        int confidence,
        List<String> affectedServices,
        List<String> recommendations,
        boolean verified,
        LocalDateTime generatedAt
) {
    public static ReportResponse from(RcaReportEntity e) {
        return new ReportResponse(
                e.getId(),
                e.getIncident().getId(),
                e.getRootCause(),
                e.getConfidenceScore(),
                split(e.getImpactedServices(), ","),
                split(e.getRemediations(), ";"),
                e.isVerified(),
                e.getGeneratedAt());
    }

    private static List<String> split(String value, String separator) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(separator)).map(String::trim).toList();
    }
}
