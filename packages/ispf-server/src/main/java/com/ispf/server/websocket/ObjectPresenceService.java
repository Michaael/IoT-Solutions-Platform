package com.ispf.server.websocket;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ObjectPresenceService {

    private static final long TTL_SECONDS = 30;

    private final Map<String, Map<String, PresenceEntry>> byPath = new ConcurrentHashMap<>();

    public void heartbeat(String path, String username, String mode) {
        if (path == null || path.isBlank() || username == null || username.isBlank()) {
            return;
        }
        String normalizedMode = "edit".equalsIgnoreCase(mode) ? "edit" : "view";
        byPath.computeIfAbsent(path, ignored -> new ConcurrentHashMap<>())
                .put(username, new PresenceEntry(username, normalizedMode, Instant.now()));
        purgeExpired(path);
    }

    public void remove(String path, String username) {
        Map<String, PresenceEntry> entries = byPath.get(path);
        if (entries != null) {
            entries.remove(username);
            if (entries.isEmpty()) {
                byPath.remove(path);
            }
        }
    }

    public List<PresenceEntry> listForPath(String path) {
        purgeExpired(path);
        Map<String, PresenceEntry> entries = byPath.get(path);
        if (entries == null) {
            return List.of();
        }
        return new ArrayList<>(entries.values());
    }

    public Map<String, List<PresenceEntry>> snapshot() {
        byPath.keySet().forEach(this::purgeExpired);
        Map<String, List<PresenceEntry>> result = new ConcurrentHashMap<>();
        byPath.forEach((path, entries) -> result.put(path, List.copyOf(entries.values())));
        return result;
    }

    private void purgeExpired(String path) {
        Map<String, PresenceEntry> entries = byPath.get(path);
        if (entries == null) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        entries.entrySet().removeIf(entry -> entry.getValue().lastSeen().isBefore(cutoff));
        if (entries.isEmpty()) {
            byPath.remove(path);
        }
    }

    public record PresenceEntry(String username, String mode, Instant lastSeen) {
    }
}
