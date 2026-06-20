package com.ispf.driver.pop3;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping: {@code stat} or {@code retr:N}.
 */
public record Pop3Point(Kind kind, int messageNumber) {

    public enum Kind {
        STAT,
        RETR
    }

    private static final Pattern RETR_PATTERN = Pattern.compile("^retr:(\\d+)$", Pattern.CASE_INSENSITIVE);

    public static Pop3Point parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("POP3 point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("stat".equalsIgnoreCase(trimmed)) {
            return new Pop3Point(Kind.STAT, 0);
        }
        Matcher matcher = RETR_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return new Pop3Point(Kind.RETR, Integer.parseInt(matcher.group(1)));
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("retr ")) {
            return new Pop3Point(Kind.RETR, Integer.parseInt(trimmed.substring(5).trim()));
        }
        throw new IllegalArgumentException("Unknown POP3 point mapping: " + raw);
    }
}
