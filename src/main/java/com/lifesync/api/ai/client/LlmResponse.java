package com.lifesync.api.ai.client;

public record LlmResponse(
        String content,
        int inputTokens,
        int outputTokens,
        String modelName,
        long latencyMs
) {}