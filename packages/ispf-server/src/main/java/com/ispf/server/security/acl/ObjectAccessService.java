package com.ispf.server.security.acl;

import com.ispf.server.config.IspfRoles;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ObjectAccessService {

    private final ObjectAclStore aclStore;

    public ObjectAccessService(ObjectAclStore aclStore) {
        this.aclStore = aclStore;
    }

    public List<ObjectAclStore.ObjectAclEntry> listEntries(String objectPath) {
        return aclStore.listByPath(objectPath);
    }

    public void replaceEntries(String objectPath, List<ObjectAclStore.ObjectAclEntryDraft> drafts) {
        for (ObjectAclStore.ObjectAclEntryDraft draft : drafts) {
            validateDraft(draft);
        }
        aclStore.replaceEntries(objectPath, drafts);
    }

    public void requireRead(String objectPath, Authentication authentication) {
        if (!canRead(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Read access denied for " + objectPath);
        }
    }

    public void requireWrite(String objectPath, Authentication authentication) {
        if (!canWrite(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Write access denied for " + objectPath);
        }
    }

    public void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    public void requireGrantAcl(String objectPath, Authentication authentication) {
        if (isAdmin(authentication) || canGrant(objectPath, authentication)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACL grant denied for " + objectPath);
    }

    public void requireInvoke(String objectPath, Authentication authentication) {
        if (!canInvoke(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoke access denied for " + objectPath);
        }
    }

    public boolean canRead(String objectPath, Authentication authentication) {
        return hasPermission(objectPath, "READ", authentication);
    }

    public boolean canWrite(String objectPath, Authentication authentication) {
        return hasPermission(objectPath, "WRITE", authentication);
    }

    public boolean canInvoke(String objectPath, Authentication authentication) {
        return hasPermission(objectPath, "INVOKE", authentication);
    }

    public boolean canGrant(String objectPath, Authentication authentication) {
        return isAdmin(authentication) || hasPermission(objectPath, "OWNER", authentication);
    }

    private boolean hasPermission(String objectPath, String permission, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        List<ObjectAclStore.ObjectAclEntry> entries = aclStore.findEffectiveEntries(objectPath);
        if (entries.isEmpty()) {
            return true;
        }
        Set<String> roles = extractRoles(authentication);
        String username = authentication != null ? authentication.getName() : null;
        for (ObjectAclStore.ObjectAclEntry entry : entries) {
            if (!matchesPermission(entry.permission(), permission)) {
                continue;
            }
            if ("ROLE".equalsIgnoreCase(entry.principalType()) && roles.contains(entry.principalId())) {
                return true;
            }
            if ("USER".equalsIgnoreCase(entry.principalType())
                    && username != null
                    && username.equalsIgnoreCase(entry.principalId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPermission(String granted, String required) {
        if (granted == null || required == null) {
            return false;
        }
        String normalizedGranted = granted.toUpperCase();
        String normalizedRequired = required.toUpperCase();
        if (normalizedGranted.equals(normalizedRequired)) {
            return true;
        }
        return switch (normalizedGranted) {
            case "OWNER" -> Set.of("READ", "WRITE", "INVOKE", "EDITOR", "VIEWER", "OWNER").contains(normalizedRequired);
            case "EDITOR" -> Set.of("READ", "WRITE", "INVOKE", "EDITOR", "VIEWER").contains(normalizedRequired);
            case "VIEWER" -> Set.of("READ", "VIEWER").contains(normalizedRequired);
            default -> false;
        };
    }

    private static boolean isAdmin(Authentication authentication) {
        return extractRoles(authentication).contains(IspfRoles.ADMIN);
    }

    private static Set<String> extractRoles(Authentication authentication) {
        Set<String> roles = new HashSet<>();
        if (authentication == null) {
            return roles;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                roles.add(value.substring("ROLE_".length()));
            } else {
                roles.add(value);
            }
        }
        return roles;
    }

    private static void validateDraft(ObjectAclStore.ObjectAclEntryDraft draft) {
        if (!Set.of("ROLE", "USER").contains(draft.principalType().toUpperCase())) {
            throw new IllegalArgumentException("principalType must be ROLE or USER");
        }
        if (!Set.of("READ", "WRITE", "INVOKE", "OWNER", "EDITOR", "VIEWER")
                .contains(draft.permission().toUpperCase())) {
            throw new IllegalArgumentException(
                    "permission must be READ, WRITE, INVOKE, OWNER, EDITOR, or VIEWER"
            );
        }
        if (draft.principalId() == null || draft.principalId().isBlank()) {
            throw new IllegalArgumentException("principalId is required");
        }
    }
}
