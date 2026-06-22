package com.ispf.server.ai.llm;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopLlmProvider implements LlmProvider {

    public static final String PROVIDER_ID = "noop";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<LlmModelInfo> listModels() {
        return List.of();
    }

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        throw new LlmException("LLM provider is not configured (noop)");
    }
}
