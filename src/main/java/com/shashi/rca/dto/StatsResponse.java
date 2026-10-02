package com.shashi.rca.dto;

/**
 * totalErrors: error incidents stored. llmAnalyses: incidents sent to the LLM (originals).
 * reusedReports: incidents that reused an earlier report because they had the same error signature.
 */
public record StatsResponse(long totalErrors, long llmAnalyses, long reusedReports, double reductionPercent) {}
