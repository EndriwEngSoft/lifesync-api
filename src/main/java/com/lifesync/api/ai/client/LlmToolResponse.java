package com.lifesync.api.ai.client;

import java.util.List;

/**
 * Resposta de uma chamada com ferramentas: ou o modelo pede ferramentas
 * (toolCalls), ou responde em texto. truncated indica resposta cortada no
 * teto de tokens. rawModelTurn e' o turno do modelo como veio do provedor,
 * para ser devolvido intacto no passo seguinte.
 */
public record LlmToolResponse(String text, List<ToolCall> toolCalls, Object rawModelTurn, boolean truncated,
                              int inputTokens, int outputTokens, String modelName, long latencyMs) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
