package com.ispf.driver.imap;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping: {@code messageCount}, {@code UNSEEN}, or {@code subject:N}.
 */
public record ImapPoint(Kind kind, int messageNumber) {

    public enum Kind {
        MESSAGE_COUNT,
        UNSEEN_COUNT,
        SUBJECT
    }

    private static final Pattern SUBJECT_PATTERN = Pattern.compile("^subject:(\\d+)$", Pattern.CASE_INSENSITIVE);

    public static ImapPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IMAP point mapping is blank");
        }
        String trimmed = raw.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if ("messagecount".equals(normalized) || "message_count".equals(normalized) || "count".equals(normalized)) {
            return new ImapPoint(Kind.MESSAGE_COUNT, 0);
        }
        if ("unseen".equals(normalized)) {
            return new ImapPoint(Kind.UNSEEN_COUNT, 0);
        }
        Matcher matcher = SUBJECT_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return new ImapPoint(Kind.SUBJECT, Integer.parseInt(matcher.group(1)));
        }
        throw new IllegalArgumentException("Unknown IMAP point mapping: " + raw);
    }
}
