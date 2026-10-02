package com.shashi.rca.service;

import java.util.Locale;

/**
 * Turns nested Gemini / HTTP errors into a short label for logs (never includes secrets).
 */
public final class LlmFailureMessages {

    public enum Category {
        MISSING_API_KEY,
        INVALID_API_KEY,
        RATE_LIMIT_OR_OVERLOAD,
        TIMEOUT,
        RESPONSE_PARSE,
        NETWORK,
        UNKNOWN
    }

    private LlmFailureMessages() {}

    public static Category categorize(Throwable error) {
        String text = fullMessage(error).toLowerCase(Locale.ROOT);
        if (text.contains("spring_ai_google_genai_api_key")
                || text.contains("api key must be set")
                || text.contains("api key not set")
                || text.contains("could not resolve placeholder")) {
            return Category.MISSING_API_KEY;
        }
        if (text.contains("api key not valid")
                || text.contains("api_key_invalid")
                || text.contains("permission_denied")
                || text.contains("401")
                || text.contains("403")
                || text.contains("unauthenticated")
                || text.contains("invalid api key")) {
            return Category.INVALID_API_KEY;
        }
        if (text.contains("high demand")
                || text.contains("overloaded")
                || text.contains("resource exhausted")
                || text.contains("resource_exhausted")
                || text.contains("429")
                || text.contains("503")
                || text.contains("rate limit")
                || text.contains("quota")) {
            return Category.RATE_LIMIT_OR_OVERLOAD;
        }
        if (text.contains("timeout")
                || text.contains("timed out")
                || text.contains("deadline")) {
            return Category.TIMEOUT;
        }
        if (text.contains("json")
                || text.contains("parse")
                || text.contains("beanoutputconverter")
                || text.contains("structured")
                || text.contains("could not parse")) {
            return Category.RESPONSE_PARSE;
        }
        if (text.contains("connection")
                || text.contains("unknown host")
                || text.contains("network")) {
            return Category.NETWORK;
        }
        return Category.UNKNOWN;
    }

    public static String userHint(Category category) {
        return switch (category) {
            case MISSING_API_KEY ->
                    "Set SPRING_AI_GOOGLE_GENAI_API_KEY in the same terminal before starting the app.";
            case INVALID_API_KEY ->
                    "Gemini rejected the API key (401/403). Create a new key in Google AI Studio.";
            case RATE_LIMIT_OR_OVERLOAD ->
                    "Gemini free tier quota hit (often 5 chat requests/min per model). "
                    + "Wait 60s between manual tests; each Kafka retry counts as another request.";
            case TIMEOUT ->
                    "Gemini did not answer within the configured timeout. Retry or increase rca.llm.timeout.";
            case RESPONSE_PARSE ->
                    "Gemini answered but JSON did not match RcaReport. Check logs for the raw response hint.";
            case NETWORK ->
                    "Could not reach Google APIs. Check internet, proxy, or firewall.";
            case UNKNOWN ->
                    "See the exception message below for details.";
        };
    }

    public static String summarize(Throwable error) {
        Category category = categorize(error);
        return category.name() + ": " + userHint(category) + " Detail: " + rootMessage(error);
    }

    private static String fullMessage(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            sb.append(t.getClass().getSimpleName()).append(' ');
        }
        return sb.toString();
    }

    private static String rootMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
