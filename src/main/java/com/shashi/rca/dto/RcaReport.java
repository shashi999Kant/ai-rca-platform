package com.shashi.rca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RcaReport(
        @JsonProperty("rootCause") String rootCause,
        @JsonProperty("confidence") int confidence,
        @JsonProperty("affectedServices") List<String> affectedServices,
        @JsonProperty("recommendations") List<String> recommendations
) {}
