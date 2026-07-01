package com.ispf.server.ai.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentSession {

    private static final String DEFAULT_TITLE = "New chat";
    private static final int TITLE_MAX_LEN = 60;

    private final String sessionId;
    private final String actor;
    private final Instant createdAt;
    private final AgentRunState runState;
    private final List<AgentTurn> turns = new ArrayList<>();

    private String rootPath;
    private String title;
    private Instant updatedAt;

    private AgentSession(
            String sessionId,
            String actor,
            String rootPath,
            String title,
            Instant createdAt,
            Instant updatedAt,
            AgentRunState runState
    ) {
        this.sessionId = sessionId;
        this.actor = actor;
        this.rootPath = rootPath != null && !rootPath.isBlank() ? rootPath.trim() : "root";
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.runState = runState;
    }

    public static AgentSession create(String actor, String rootPath) {
        return new AgentSession(UUID.randomUUID().toString(), actor, rootPath, DEFAULT_TITLE, Instant.now(), Instant.now(), new AgentRunState());
    }

    public static AgentSession restore(
            String sessionId,
            String actor,
            String rootPath,
            String title,
            Instant createdAt,
            Instant updatedAt,
            AgentRunState runState,
            List<AgentTurn> restoredTurns
    ) {
        AgentSession session = new AgentSession(
                sessionId,
                actor,
                rootPath,
                title != null && !title.isBlank() ? title : DEFAULT_TITLE,
                createdAt != null ? createdAt : Instant.now(),
                updatedAt != null ? updatedAt : Instant.now(),
                runState != null ? runState : new AgentRunState()
        );
        if (restoredTurns != null) {
            synchronized (session.turns) {
                session.turns.addAll(restoredTurns);
            }
        }
        return session;
    }

    public String sessionId() {
        return sessionId;
    }

    public String actor() {
        return actor;
    }

    public String rootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        if (rootPath != null && !rootPath.isBlank()) {
            this.rootPath = rootPath.trim();
        }
    }

    public String title() {
        return title;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public AgentRunState runState() {
        return runState;
    }

    public List<AgentTurn> turns() {
        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    public void addTurn(AgentTurn turn) {
        synchronized (turns) {
            turns.add(turn);
            if (DEFAULT_TITLE.equals(title) && turn.userMessage() != null && !turn.userMessage().isBlank()) {
                title = truncateTitle(turn.userMessage());
            }
            updatedAt = Instant.now();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", sessionId);
        map.put("title", title);
        map.put("rootPath", rootPath);
        map.put("createdAt", createdAt.toString());
        map.put("updatedAt", updatedAt.toString());
        map.put("turns", turns().stream().map(AgentTurn::toMap).toList());
        map.put("hasPendingRun", runState.hasPending());
        map.put("planState", runState.planStateSummary());
        return map;
    }

    public Map<String, Object> toSummaryMap() {
        return Map.of(
                "sessionId", sessionId,
                "title", title,
                "rootPath", rootPath,
                "createdAt", createdAt.toString(),
                "updatedAt", updatedAt.toString()
        );
    }

    private static String truncateTitle(String text) {
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= TITLE_MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, TITLE_MAX_LEN - 1) + "…";
    }
}
