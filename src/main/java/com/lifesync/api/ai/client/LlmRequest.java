package com.lifesync.api.ai.client;

public record LlmRequest(
        String systemPrompt,
        String userContextJson
) {}