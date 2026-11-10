package com.lifesync.api.ai.client;

import java.util.Map;

/** Pedido do modelo para executar uma ferramenta. Nome e argumentos sao entrada nao confiavel. */
public record ToolCall(String id, String name, Map<String, Object> args) {}
