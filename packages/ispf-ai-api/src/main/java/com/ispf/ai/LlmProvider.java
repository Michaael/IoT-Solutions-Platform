package com.ispf.ai;

import java.util.List;

/**
 * Service Provider Interface for LLM backends (OpenAI-compatible, Ollama, custom URL).
 * Implementations live outside {@code ispf-server} core.
 */
public interface LlmProvider {

    String providerId();

    boolean isAvailable();

    List<LlmModelInfo> listModels() throws LlmException;

    LlmResponse complete(LlmRequest request) throws LlmException;
}
