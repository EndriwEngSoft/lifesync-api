package com.lifesync.api.ai.client;

import java.util.Map;

/**
 * Pedido ao provedor, neutro de fornecedor.
 *
 * @param systemPrompt    instrucoes fixas (vao no campo proprio do provedor)
 * @param userContextJson dados do usuario, ja minimizados, em JSON
 * @param responseSchema  JSON Schema da saida esperada
 */
public record LlmRequest(
        String systemPrompt,
        String userContextJson,
        Map<String, Object> responseSchema
) {}
