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
    private int timeoutSeconds = 600;
    private int maxTokens = 65536;
    /** Max completion tokens per agent turn (~half of a 256k context window; prompt uses the rest). */
    private int agentMaxTokens = 131_072;
    private double temperature = 0.0;
    private String contextPackClasspath = "classpath:ai/context-pack.json";
    private int agentMaxSteps = 256;
    private int agentSessionTtlHours = 24;
    private int agentMaxHistoryTurns = 128;
    private boolean agentDisableThinking = true;
    private int agentParseRetries = 5;
    private int briefingMaxChars = 32_768;
    private boolean briefingEveryTurn = false;
    /** When set, overrides vision capability detection from model name. */
    private Boolean agentVisionEnabled;
    private int agentMaxAttachmentBytes = 32 * 1024 * 1024;
    /** ~512 KB of spec/TZ text inlined into the user message (chars, not tokens). */
    private int agentMaxTextInjectChars = 524_288;

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

    public int getAgentMaxTokens() {
        return agentMaxTokens > 0 ? agentMaxTokens : maxTokens;
    }

    public void setAgentMaxTokens(int agentMaxTokens) {
        this.agentMaxTokens = agentMaxTokens;
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

    public int getAgentParseRetries() {
        return agentParseRetries;
    }

    public void setAgentParseRetries(int agentParseRetries) {
        this.agentParseRetries = agentParseRetries;
    }

    public int getBriefingMaxChars() {
        return briefingMaxChars;
    }

    public void setBriefingMaxChars(int briefingMaxChars) {
        this.briefingMaxChars = briefingMaxChars;
    }

    public boolean isBriefingEveryTurn() {
        return briefingEveryTurn;
    }

    public void setBriefingEveryTurn(boolean briefingEveryTurn) {
        this.briefingEveryTurn = briefingEveryTurn;
    }

    public Boolean getAgentVisionEnabled() {
        return agentVisionEnabled;
    }

    public void setAgentVisionEnabled(Boolean agentVisionEnabled) {
        this.agentVisionEnabled = agentVisionEnabled;
    }

    public int getAgentMaxAttachmentBytes() {
        return agentMaxAttachmentBytes;
    }

    public void setAgentMaxAttachmentBytes(int agentMaxAttachmentBytes) {
        this.agentMaxAttachmentBytes = agentMaxAttachmentBytes;
    }

    public int getAgentMaxTextInjectChars() {
        return agentMaxTextInjectChars;
    }

    public void setAgentMaxTextInjectChars(int agentMaxTextInjectChars) {
        this.agentMaxTextInjectChars = agentMaxTextInjectChars;
    }

    public Duration timeout() {
        return Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }
}
