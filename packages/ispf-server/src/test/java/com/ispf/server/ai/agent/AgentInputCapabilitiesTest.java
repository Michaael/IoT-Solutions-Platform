package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInputCapabilitiesTest {

    @Test
    void probedVisionFalseForTextOnlyModel() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("unsloth/Qwen3.6-35B-A3B-NVFP4");
        assertThat(AgentInputCapabilities.visionEnabled(properties, "openai-compatible", false)).isFalse();
    }

    @Test
    void probedVisionTrueWhenProviderReportsSupport() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("custom-model");
        assertThat(AgentInputCapabilities.visionEnabled(properties, "openai-compatible", true)).isTrue();
    }

    @Test
    void explicitOverrideDisablesVisionEvenWhenProbedTrue() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("gpt-4o");
        properties.setAgentVisionEnabled(false);
        assertThat(AgentInputCapabilities.visionEnabled(properties, "openai-compatible", true)).isFalse();
    }

    @Test
    void supportedTypesIncludeImagesOnlyWhenVisionEnabled() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("gpt-4o");
        var types = AgentInputCapabilities.supportedAttachmentTypes(properties, "openai-compatible", true);
        assertThat(types).extracting(map -> map.get("kind")).contains("text", "image");
    }

    @Test
    void noopProviderHasNoAttachmentTypes() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        assertThat(AgentInputCapabilities.supportedAttachmentTypes(properties, "noop", false)).isEmpty();
    }

    @Test
    void nameHeuristicStillDetectsKnownVisionModels() {
        assertThat(AgentInputCapabilities.modelSupportsVision("gpt-4o-mini")).isTrue();
        assertThat(AgentInputCapabilities.modelSupportsVision("unsloth/Qwen3.6-35B-A3B-NVFP4")).isFalse();
    }
}
