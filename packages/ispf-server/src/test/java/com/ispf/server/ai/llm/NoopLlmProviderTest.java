package com.ispf.server.ai.llm;

import com.ispf.ai.LlmException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NoopLlmProviderTest {

    @Test
    void noopProviderIsUnavailable() {
        NoopLlmProvider provider = new NoopLlmProvider();
        assertEquals("noop", provider.providerId());
        assertFalse(provider.isAvailable());
    }

    @Test
    void completeThrowsConfigurationError() {
        NoopLlmProvider provider = new NoopLlmProvider();
        try {
            provider.complete(new com.ispf.ai.LlmRequest("test", java.util.List.of(), 100, 0.1));
            org.junit.jupiter.api.Assertions.fail("expected LlmException");
        } catch (LlmException ex) {
            assertEquals("LLM provider is not configured (noop)", ex.getMessage());
        }
    }
}
