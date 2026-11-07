package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.exception.AiDisabledException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Porteiro do modulo de IA. Falha fechada: com a flag desligada, ou sem
 * nenhum LlmClient registrado, devolve 503 antes de tocar no banco ou na
 * rede. O LlmClient so e pedido ao container depois que a flag passa, entao
 * com o modulo desligado nada do provedor e' usado.
 */
@Component
public class AiAvailability {

    private final AiProperties properties;
    private final ObjectProvider<LlmClient> llmClient;

    public AiAvailability(AiProperties properties, ObjectProvider<LlmClient> llmClient) {
        this.properties = properties;
        this.llmClient = llmClient;
    }

    public LlmClient requireClient() {
        if (!properties.enabled()) {
            throw new AiDisabledException("AI module is disabled.");
        }
        LlmClient client = llmClient.getIfAvailable();
        if (client == null) {
            throw new AiDisabledException("AI provider is not configured.");
        }
        return client;
    }
}
