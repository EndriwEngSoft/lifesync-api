package com.lifesync.api.ai.client;

import java.util.Map;

/** Ferramenta oferecida ao modelo: nome, descricao e JSON Schema dos argumentos. */
public record ToolSpec(String name, String description, Map<String, Object> parametersSchema) {}
