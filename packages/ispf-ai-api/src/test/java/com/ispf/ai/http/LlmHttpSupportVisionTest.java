package com.ispf.ai.http;

import com.ispf.ai.LlmException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmHttpSupportVisionTest {

    @Test
    void parsesOllamaShowVisionCapability() throws LlmException {
        String json = """
                {
                  "capabilities": ["completion", "vision"]
                }
                """;
        assertTrue(LlmHttpSupport.parseOllamaShowVision(json));
    }

    @Test
    void parsesOpenRouterModalityMetadata() throws LlmException {
        String json = """
                {
                  "data": [
                    {
                      "id": "openai/gpt-4o-mini",
                      "architecture": { "modality": "text+image->text" }
                    }
                  ]
                }
                """;
        assertTrue(LlmHttpSupport.visionFromOpenAiModelsList(json, "openai/gpt-4o-mini"));
    }

    @Test
    void textOnlyModalityIsNotVision() throws LlmException {
        String json = """
                {
                  "data": [
                    {
                      "id": "meta/llama-3",
                      "architecture": { "modality": "text->text" }
                    }
                  ]
                }
                """;
        assertFalse(LlmHttpSupport.visionFromOpenAiModelsList(json, "meta/llama-3"));
    }

    @Test
    void probeSuccessMeansVisionSupported() throws LlmException {
        assertTrue(LlmHttpSupport.interpretVisionProbeResult(200, "{\"choices\":[]}"));
    }

    @Test
    void probeVisionRejectionMeansNotSupported() throws LlmException {
        assertFalse(LlmHttpSupport.interpretVisionProbeResult(
                400,
                "{\"error\":{\"message\":\"This model does not support image inputs\"}}"
        ));
    }
}
