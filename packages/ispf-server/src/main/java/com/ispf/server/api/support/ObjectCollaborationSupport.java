package com.ispf.server.api.support;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.object.ObjectRevisionContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class ObjectCollaborationSupport {

    private ObjectCollaborationSupport() {
    }

    public static void bindWriteContext(Authentication authentication, HttpHeaders headers) {
        ObjectRevisionContext.setActor(authentication != null ? authentication.getName() : "system");
        ObjectRevisionContext.setExpectation(parseIfMatch(headers), isForceOverwrite(headers, authentication));
    }

    public static void clearContext() {
        ObjectRevisionContext.clear();
    }

    public static Long parseIfMatch(HttpHeaders headers) {
        String raw = headers.getFirst(HttpHeaders.IF_MATCH);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isForceOverwrite(HttpHeaders headers, Authentication authentication) {
        String force = headers.getFirst("X-ISPF-Force");
        if (force == null || !Boolean.parseBoolean(force)) {
            return false;
        }
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (("ROLE_" + IspfRoles.ADMIN).equals(authority.getAuthority())
                    || IspfRoles.ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
