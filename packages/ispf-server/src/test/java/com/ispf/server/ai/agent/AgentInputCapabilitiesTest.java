package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInputCapabilitiesTest {

    @Test
    void visionDisabledForTextOnlyModelByDefault() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("unsloth/Qwen3.6-35B-A3B-NVFP4");
        assertThat(AgentInputCapabilities.visionEnabled(properties, "openai-compatible")).isFalse();
    }

    @Test
    void visionEnabledForGpt4oHeuristic() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("gpt-4o-mini");
        assertThat(AgentInputCapabilities.visionEnabled(properties, "openai-compatible")).isTrue();
    }

    @Test
    void explicitOverrideDisablesVisionEvenForGpt4o() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("gpt-4o");
        properties.setAgentVisionEnabled(false);
        assertThat(AgentInputCapabilities.visionEnabled(properties, "openai-compatible")).isFalse();
    }

    @Test
    void supportedTypesIncludeImagesOnlyWhenVisionEnabled() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("gpt-4o");
        var types = AgentInputCapabilities.supportedAttachmentTypes(properties, "openai-compatible");
        assertThat(types).extracting(map -> map.get("kind")).contains("text", "image");
    }

    @Test
    void noopProviderHasNoAttachmentTypes() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        assertThat(AgentInputCapabilities.supportedAttachmentTypes(properties, "noop")).isEmpty();
    }
}
