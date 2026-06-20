package com.ispf.driver.webtransaction;

import java.util.Locale;

/**
 * One HTTP step in a multi-step transaction.
 */
public record WebTransactionStep(String name, String method, String url, String body) {

    public static WebTransactionStep parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Web transaction step token is blank");
        }
        String trimmed = token.trim();
        int firstColon = trimmed.indexOf(':');
        if (firstColon <= 0) {
            throw new IllegalArgumentException("Invalid step token: " + trimmed);
        }
        String name = trimmed.substring(0, firstColon).trim();
        String remainder = trimmed.substring(firstColon + 1).trim();
        int secondColon = remainder.indexOf(':');
        if (secondColon <= 0) {
            throw new IllegalArgumentException("Invalid step token (missing method): " + trimmed);
        }
        String method = remainder.substring(0, secondColon).trim().toUpperCase(Locale.ROOT);
        String rest = remainder.substring(secondColon + 1).trim();
        String url;
        String body = "";
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            int bodyColon = rest.lastIndexOf(':');
            if (bodyColon > 0) {
                url = rest.substring(0, bodyColon).trim();
                body = rest.substring(bodyColon + 1);
            } else {
                url = rest;
            }
        } else {
            url = rest;
        }
        if (name.isBlank() || method.isBlank() || url.isBlank()) {
            throw new IllegalArgumentException("Invalid step token: " + trimmed);
        }
        return new WebTransactionStep(name, method, url, body);
    }
}
