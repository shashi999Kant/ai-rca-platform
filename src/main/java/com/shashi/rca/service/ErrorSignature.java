package com.shashi.rca.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns "timeout after 5012 ms for request 3f2a..." and "timeout after 4870 ms for request 9c1b..."
 * into the same fingerprint, so repeated errors can share one RCA report.
 */
public final class ErrorSignature {

    // Order matters: timestamps and UUIDs contain digits, so they are replaced before plain numbers
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[t ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern HEX = Pattern.compile("\\b0x[0-9a-f]+\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ErrorSignature() {}

    public static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String s = message.toLowerCase(Locale.ROOT);
        s = UUID.matcher(s).replaceAll("<uuid>");
        s = TIMESTAMP.matcher(s).replaceAll("<ts>");
        s = HEX.matcher(s).replaceAll("<hex>");
        s = NUMBER.matcher(s).replaceAll("<n>");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    public static String of(String service, String exception, String message) {
        String input = service + "|" + exception + "|" + normalize(message);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
