package com.lifesync.api.ai.client;

import java.util.Map;

/** Resultado de uma ferramenta, devolvido ao modelo no turno seguinte. */
public record ToolResult(String callId, String name, Map<String, Object> response) {}
