package com.lifesync.api.ai.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o render.yaml: nenhuma variavel do modulo de IA tem valor no arquivo
 * versionado. Todas sao "sync: false", entao quem manda nelas e o painel do
 * Render, e um sync do Blueprint nao sobrescreve o que foi definido la.
 *
 * Sem essas variaveis o modulo nasce desligado e sem provider por causa dos
 * padroes do application-prod.yml (ver ProductionAiDefaultsTest).
 */
class RenderBlueprintTest {

    private static final List<String> AI_VARIABLES = List.of(
            "LIFESYNC_AI_ENABLED", "LIFESYNC_AI_PROVIDER", "LIFESYNC_AI_ALLOWED_USERS", "LLM_API_KEY");

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> envVars() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("render.yaml"))) {
            Map<String, Object> root = new Yaml().load(reader);
            Map<String, Object> service = ((List<Map<String, Object>>) root.get("services")).getFirst();
            Map<String, Map<String, Object>> byKey = new java.util.LinkedHashMap<>();
            for (Map<String, Object> env : (List<Map<String, Object>>) service.get("envVars")) {
                byKey.put((String) env.get("key"), env);
            }
            return byKey;
        }
    }

    @Test
    void aiVariables_AreDeclaredWithoutAnyValueInTheRepository() throws IOException {
        var env = envVars();

        for (String key : AI_VARIABLES) {
            assertThat(env).as(key).containsKey(key);
            assertThat(env.get(key).get("sync")).as(key + " sync").isEqualTo(false);
            assertThat(env.get(key)).as(key + " value").doesNotContainKey("value");
        }
    }

    @Test
    void noOtherAiVariable_HasAValueInTheRepository() throws IOException {
        // Uma variavel de IA nova com "value" voltaria a ser sobrescrita a cada sync
        // e poderia ligar o modulo por arquivo versionado.
        envVars().forEach((key, env) -> {
            if (key.startsWith("LIFESYNC_AI_") || key.startsWith("LLM_")) {
                assertThat(env).as(key + " value").doesNotContainKey("value");
            }
        });
    }
}
