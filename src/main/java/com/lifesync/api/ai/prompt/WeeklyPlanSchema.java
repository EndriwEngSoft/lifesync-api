package com.lifesync.api.ai.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema da saida do plano semanal, enviado ao provedor para guiar a
 * resposta. E' so um guia: o provedor pode ignorar partes do schema (o
 * Gemini descarta minLength/maxLength, por exemplo), entao a resposta SEMPRE
 * e' validada de novo no servidor. Usa so o subconjunto basico (object,
 * array, string, enum, required) para nao ser rejeitado por complexidade.
 */
public final class WeeklyPlanSchema {

    public static final List<String> DAYS =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    private WeeklyPlanSchema() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> days = new LinkedHashMap<>();
        for (String day : DAYS) {
            days.put(day, arrayOf(Map.of("type", "string")));
        }

        Map<String, Object> priority = obj(
                Map.of(
                        "type", Map.of("type", "string", "enum", List.of("TASK", "HABIT", "GOAL")),
                        "referenceId", Map.of("type", "string"),
                        "reason", Map.of("type", "string")),
                List.of("type", "referenceId", "reason"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summary", Map.of("type", "string"));
        root.put("priorities", arrayOf(priority));
        root.put("risks", arrayOf(Map.of("type", "string")));
        root.put("schedule", obj(days, List.of()));
        return obj(root, List.of("summary", "priorities", "risks", "schedule"));
    }

    private static Map<String, Object> obj(Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        if (!required.isEmpty()) {
            m.put("required", required);
        }
        return m;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }
}
