package com.lifesync.api.ai.client;

import java.util.List;

/**
 * Chamada com ferramentas: instrucoes fixas, a pergunta do usuario, as
 * ferramentas disponiveis e os passos ja executados nesta conversa.
 */
public record LlmToolRequest(String systemPrompt, String userMessage, List<ToolSpec> tools,
                             List<ToolExchange> history) {}
