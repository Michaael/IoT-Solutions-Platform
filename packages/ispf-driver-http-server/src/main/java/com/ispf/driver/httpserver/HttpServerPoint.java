package com.ispf.driver.httpserver;

import java.util.Locale;

/**
 * Point mapping: {@code requests}, {@code lastPath}, or {@code lastBody}.
 */
public record HttpServerPoint(HttpServerMetric metric) {

    public enum HttpServerMetric {
        REQUESTS,
        LAST_PATH,
        LAST_BODY
    }

    public static HttpServerPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("HTTP server point mapping is blank");
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "requests" -> new HttpServerPoint(HttpServerMetric.REQUESTS);
            case "lastpath" -> new HttpServerPoint(HttpServerMetric.LAST_PATH);
            case "lastbody" -> new HttpServerPoint(HttpServerMetric.LAST_BODY);
            default -> throw new IllegalArgumentException("Unknown HTTP server metric: " + raw);
        };
    }
}
