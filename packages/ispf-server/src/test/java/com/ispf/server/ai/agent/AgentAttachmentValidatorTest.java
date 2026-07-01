package com.ispf.server.ai.agent;

import com.ispf.ai.LlmProvider;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAttachmentValidatorTest {

    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private LlmProvider llmProvider;

    private AiProperties properties;
    private AgentAttachmentValidator validator;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("unsloth/Qwen3.6-35B-A3B-NVFP4");
        properties.setAgentVisionEnabled(false);
        validator = new AgentAttachmentValidator(properties, llmProviderRegistry);
        when(llmProviderRegistry.activeProvider()).thenReturn(llmProvider);
        when(llmProvider.providerId()).thenReturn("openai-compatible");
        when(llmProviderRegistry.visionEnabled()).thenReturn(false);
    }

    @Test
    void rejectsImageWhenVisionDisabled() {
        String base64 = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        assertThatThrownBy(() -> validator.prepare(
                "analyze",
                List.of(new AgentAttachmentValidator.AttachmentInput("pic.png", "image/png", base64))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vision-not-supported");
    }

    @Test
    void injectsTextAttachmentIntoPrompt() {
        String json = "{\"appId\":\"demo\"}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        AgentAttachmentValidator.PreparedUserMessage prepared = validator.prepare(
                "validate bundle",
                List.of(new AgentAttachmentValidator.AttachmentInput("bundle.json", "application/json", base64))
        );
        assertThat(prepared.llmText()).contains("validate bundle");
        assertThat(prepared.llmText()).contains("demo");
        assertThat(prepared.attachmentMetadata()).hasSize(1);
        assertThat(prepared.attachmentMetadata().getFirst().get("kind")).isEqualTo("text");
    }
}
