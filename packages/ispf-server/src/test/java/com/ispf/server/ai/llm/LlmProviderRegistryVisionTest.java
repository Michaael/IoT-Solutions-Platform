package com.ispf.server.ai.llm;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderRegistryVisionTest {

    private AiProperties properties;
    private RecordingLlmProvider provider;
    private LlmProviderRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai-compatible");
        properties.setBaseUrl("http://llm.test/v1");
        properties.setModel("custom-vision-model");
        provider = new RecordingLlmProvider(true);
        registry = new LlmProviderRegistry(properties, new NoopLlmProvider()) {
            @Override
            public LlmProvider activeProvider() {
                return provider;
            }

            @Override
            public boolean isGenerationAvailable() {
                return true;
            }
        };
    }

    @Test
    void usesLiveProviderProbeWhenAvailable() {
        assertThat(registry.visionEnabled()).isTrue();
        assertThat(provider.probeCount).isEqualTo(1);
        assertThat(registry.visionEnabled()).isTrue();
        assertThat(provider.probeCount).isEqualTo(1);
    }

    @Test
    void explicitOverrideSkipsProbe() {
        properties.setAgentVisionEnabled(false);
        assertThat(registry.visionEnabled()).isFalse();
        assertThat(provider.probeCount).isZero();
    }

    @Test
    void fallsBackToNameHeuristicWhenProbeFails() {
        provider = new RecordingLlmProvider(new LlmException("offline"));
        registry = new LlmProviderRegistry(properties, new NoopLlmProvider()) {
            @Override
            public LlmProvider activeProvider() {
                return provider;
            }

            @Override
            public boolean isGenerationAvailable() {
                return true;
            }
        };
        properties.setModel("gpt-4o-mini");
        assertThat(registry.visionEnabled()).isTrue();
    }

    private static final class RecordingLlmProvider implements LlmProvider {
        private final Object probeResult;
        private int probeCount;

        private RecordingLlmProvider(boolean supported) {
            this.probeResult = supported;
        }

        private RecordingLlmProvider(LlmException failure) {
            this.probeResult = failure;
        }

        @Override
        public String providerId() {
            return "openai-compatible";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<LlmModelInfo> listModels() {
            return List.of();
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supportsVision(String model) throws LlmException {
            probeCount++;
            if (probeResult instanceof LlmException ex) {
                throw ex;
            }
            return (Boolean) probeResult;
        }
    }
}
