package com.ispf.server.ai.agent;

import java.util.Locale;

/**
 * Planning lifecycle for a multi-turn agent session.
 */
public enum AgentPlanPhase {

    /** No active plan workflow. */
    NONE("none"),
    /** Discovery + drafting plan; mutations blocked. */
    PLANNING("planning"),
    /** Plan presented; waiting for user approval or answers. */
    AWAITING_APPROVAL("awaiting_approval"),
    /** User approved; mutations allowed for this goal. */
    APPROVED("approved");

    private final String storageValue;

    AgentPlanPhase(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static AgentPlanPhase fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AgentPlanPhase phase : values()) {
            if (phase.storageValue.equals(normalized)) {
                return phase;
            }
        }
        return NONE;
    }
}
