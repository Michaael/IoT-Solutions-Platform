package com.ispf.server.ai.agent;

import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session mutable state: bundle validation gates and in-flight agent continuation.
 */
public final class AgentRunState {

    private final Map<String, Boolean> validatedBundles = new ConcurrentHashMap<>();
    private volatile AgentPendingContinuation pending;
    private volatile String agentProfile = AgentProfile.ADMIN.storageValue();
    private volatile String operatorAppId;
    private volatile String interactionMode = AgentInteractionMode.AUTO.storageValue();
    private volatile String planPhase = AgentPlanPhase.NONE.storageValue();
    private volatile Map<String, Object> storedPlan = Map.of();

    public AgentInteractionMode interactionMode() {
        return AgentInteractionMode.fromString(interactionMode);
    }

    public void setInteractionMode(AgentInteractionMode mode) {
        this.interactionMode = mode != null ? mode.storageValue() : AgentInteractionMode.AUTO.storageValue();
    }

    public AgentPlanPhase planPhase() {
        return AgentPlanPhase.fromString(planPhase);
    }

    public void setPlanPhase(AgentPlanPhase phase) {
        this.planPhase = phase != null ? phase.storageValue() : AgentPlanPhase.NONE.storageValue();
    }

    public Map<String, Object> storedPlan() {
        return storedPlan == null ? Map.of() : Map.copyOf(storedPlan);
    }

    public void setStoredPlan(Map<String, Object> plan) {
        this.storedPlan = plan == null || plan.isEmpty() ? Map.of() : Map.copyOf(plan);
    }

    public boolean isPlanApproved() {
        return planPhase() == AgentPlanPhase.APPROVED;
    }

    public boolean isPlanningActive() {
        AgentPlanPhase phase = planPhase();
        return phase == AgentPlanPhase.PLANNING || phase == AgentPlanPhase.AWAITING_APPROVAL;
    }

    public void approvePlan() {
        this.planPhase = AgentPlanPhase.APPROVED.storageValue();
    }

    public void resetPlan() {
        this.planPhase = AgentPlanPhase.NONE.storageValue();
        this.storedPlan = Map.of();
    }

    public Map<String, Object> planStateSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("interactionMode", interactionMode);
        summary.put("planPhase", planPhase);
        summary.put("planApproved", isPlanApproved());
        if (storedPlan != null && !storedPlan.isEmpty()) {
            summary.put("plan", Map.copyOf(storedPlan));
        }
        return summary;
    }

    public void markBundleValidated(String appId) {
        if (appId != null && !appId.isBlank()) {
            validatedBundles.put(appId.trim(), true);
        }
    }

    public boolean isBundleValidated(String appId) {
        return appId != null && Boolean.TRUE.equals(validatedBundles.get(appId.trim()));
    }

    public Optional<AgentPendingContinuation> pending() {
        return Optional.ofNullable(pending);
    }

    public boolean hasPending() {
        return pending != null;
    }

    public void setPending(AgentPendingContinuation continuation) {
        this.pending = continuation;
    }

    public void clearPending() {
        this.pending = null;
    }

    public AgentProfile agentProfile() {
        return AgentProfile.fromString(agentProfile);
    }

    public void setAgentProfile(AgentProfile profile) {
        this.agentProfile = profile != null ? profile.storageValue() : AgentProfile.ADMIN.storageValue();
    }

    public String operatorAppId() {
        return operatorAppId;
    }

    public void setOperatorAppId(String appId) {
        this.operatorAppId = appId != null && !appId.isBlank() ? appId.trim() : null;
    }

    public Map<String, Object> snapshot(ObjectMapper objectMapper) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("validatedBundles", Map.copyOf(validatedBundles));
        map.put("agentProfile", agentProfile);
        if (operatorAppId != null && !operatorAppId.isBlank()) {
            map.put("operatorAppId", operatorAppId);
        }
        map.put("interactionMode", interactionMode);
        map.put("planPhase", planPhase);
        if (storedPlan != null && !storedPlan.isEmpty()) {
            map.put("storedPlan", Map.copyOf(storedPlan));
        }
        if (pending != null) {
            map.put("pending", pending.toMap(objectMapper));
        }
        return map;
    }

    public void restore(ObjectMapper objectMapper, Map<String, Object> raw) {
        validatedBundles.clear();
        pending = null;
        if (raw == null || raw.isEmpty()) {
            return;
        }
        Object bundlesRaw = raw.get("validatedBundles");
        if (bundlesRaw instanceof Map<?, ?> bundles) {
            for (Map.Entry<?, ?> entry : bundles.entrySet()) {
                validatedBundles.put(String.valueOf(entry.getKey()), Boolean.TRUE.equals(entry.getValue()));
            }
        } else {
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if ("pending".equals(entry.getKey())) {
                    continue;
                }
                if (entry.getValue() instanceof Boolean bool && bool) {
                    validatedBundles.put(entry.getKey(), true);
                }
            }
        }
        AgentPendingContinuation.fromMap(objectMapper, raw.get("pending")).ifPresent(value -> pending = value);
        Object profileRaw = raw.get("agentProfile");
        if (profileRaw != null) {
            agentProfile = AgentProfile.fromString(String.valueOf(profileRaw)).storageValue();
        }
        Object appRaw = raw.get("operatorAppId");
        if (appRaw != null && !String.valueOf(appRaw).isBlank()) {
            operatorAppId = String.valueOf(appRaw).trim();
        }
        Object modeRaw = raw.get("interactionMode");
        if (modeRaw != null) {
            interactionMode = AgentInteractionMode.fromString(String.valueOf(modeRaw)).storageValue();
        }
        Object phaseRaw = raw.get("planPhase");
        if (phaseRaw != null) {
            planPhase = AgentPlanPhase.fromString(String.valueOf(phaseRaw)).storageValue();
        }
        Object planRaw = raw.get("storedPlan");
        if (planRaw instanceof Map<?, ?> planMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = new LinkedHashMap<>((Map<String, Object>) planMap);
            storedPlan = plan;
        }
    }

    /** @deprecated use {@link #snapshot(ObjectMapper)} */
    @Deprecated
    public Map<String, Boolean> snapshot() {
        return Map.copyOf(validatedBundles);
    }

    /** @deprecated use {@link #restore(ObjectMapper, Map)} */
    @Deprecated
    public void restore(Map<String, Boolean> state) {
        validatedBundles.clear();
        if (state != null) {
            validatedBundles.putAll(state);
        }
    }
}
