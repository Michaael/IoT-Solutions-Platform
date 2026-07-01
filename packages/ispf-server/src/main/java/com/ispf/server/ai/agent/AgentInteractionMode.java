package com.ispf.server.ai.agent;

import java.util.Locale;

/**
 * How the platform agent interacts with the user for a session or turn.
 */
public enum AgentInteractionMode {

    /** Plan complex tasks automatically; simple requests execute immediately. */
    AUTO("auto"),
    /** Always clarify and produce a plan before mutations. */
    PLAN("plan"),
    /** Skip planning gate; execute with tools immediately — no clarifying questions. */
    EXECUTE("execute"),
    /** Read-only discovery — no tree mutations. */
    ASK("ask");

    private final String storageValue;

    AgentInteractionMode(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static AgentInteractionMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AgentInteractionMode mode : values()) {
            if (mode.storageValue.equals(normalized)) {
                return mode;
            }
        }
        return AUTO;
    }
}
