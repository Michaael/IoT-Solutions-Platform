package com.ispf.driver.ldap;

/**
 * Point mapping: LDAP filter {@code (objectClass=*)} or attribute path {@code cn=admin:mail}.
 */
public record LdapPoint(Kind kind, String filter, String attribute) {

    public enum Kind {
        FILTER_COUNT,
        ATTRIBUTE
    }

    public static LdapPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LDAP point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("(")) {
            return new LdapPoint(Kind.FILTER_COUNT, trimmed, null);
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1) {
            throw new IllegalArgumentException("LDAP attribute path requires attr=value:attribute: " + raw);
        }
        String filterPart = trimmed.substring(0, colon).trim();
        String attribute = trimmed.substring(colon + 1).trim();
        if (filterPart.isBlank() || attribute.isBlank()) {
            throw new IllegalArgumentException("LDAP attribute path is invalid: " + raw);
        }
        String filter = filterPart.startsWith("(") ? filterPart : "(" + filterPart + ")";
        return new LdapPoint(Kind.ATTRIBUTE, filter, attribute);
    }
}
