package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ispf.ai")
public class AiProperties {

    private boolean enabled = true;
    private String provider = "noop";
    private String baseUrl = "";
    private String model = "gpt-4o-mini";
    private String apiKey = "";
    private String apiKeyEnv = "OPENAI_API_KEY";
    private int timeoutSeconds = 60;
    private int maxTokens = 16384;
    private double temperature = 0.2;
    private String contextPackClasspath = "classpath:ai/context-pack.json";
    private int agentMaxSteps = 18;
    private int agentSessionTtlHours = 24;
    private int agentMaxHistoryTurns = 50;
    private boolean agentDisableThinking = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getContextPackClasspath() {
        return contextPackClasspath;
    }

    public void setContextPackClasspath(String contextPackClasspath) {
        this.contextPackClasspath = contextPackClasspath;
    }

    public int getAgentMaxSteps() {
        return agentMaxSteps;
    }

    public void setAgentMaxSteps(int agentMaxSteps) {
        this.agentMaxSteps = agentMaxSteps;
    }

    public int getAgentSessionTtlHours() {
        return agentSessionTtlHours;
    }

    public void setAgentSessionTtlHours(int agentSessionTtlHours) {
        this.agentSessionTtlHours = agentSessionTtlHours;
    }

    public int getAgentMaxHistoryTurns() {
        return agentMaxHistoryTurns;
    }

    public void setAgentMaxHistoryTurns(int agentMaxHistoryTurns) {
        this.agentMaxHistoryTurns = agentMaxHistoryTurns;
    }

    public boolean isAgentDisableThinking() {
        return agentDisableThinking;
    }

    public void setAgentDisableThinking(boolean agentDisableThinking) {
        this.agentDisableThinking = agentDisableThinking;
    }

    public Duration timeout() {
        return Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }
}
