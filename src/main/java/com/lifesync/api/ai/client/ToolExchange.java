package com.lifesync.api.ai.client;

import java.util.List;

/**
 * Um passo ja concluido da conversa: o turno do modelo que pediu ferramentas e
 * os resultados delas. O turno do modelo e' guardado como veio do provedor e
 * devolvido sem alteracao (o Gemini exige de volta a assinatura de raciocinio
 * que acompanha o pedido de ferramenta).
 */
public record ToolExchange(Object rawModelTurn, List<ToolResult> results) {}
