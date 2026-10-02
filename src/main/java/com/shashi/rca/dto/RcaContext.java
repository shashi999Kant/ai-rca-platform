package com.shashi.rca.dto;

/**
 * Everything the LLM gets to see for one incident.
 */
public record RcaContext(
        String service,
        String exception,
        String message,
        String upstreamPaths,
        String downstreamPaths,
        String similarIncidents
) {}
