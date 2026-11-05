package com.lifesync.api.ai.client;

public interface LlmClient {
    LlmResponse generatePlan(LlmRequest request);
}