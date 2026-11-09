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
 * Trava o render.yaml: o modulo de IA precisa nascer desligado em producao e a
 * chave do provedor nunca pode ter valor no arquivo versionado.
 */
class RenderBlueprintTest {

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
    void aiModule_IsDisabledWithNoProvider() throws IOException {
        var env = envVars();

        assertThat(env.get("LIFESYNC_AI_ENABLED").get("value")).isEqualTo("false");
        assertThat(env).containsKey("LIFESYNC_AI_PROVIDER");
        Object provider = env.get("LIFESYNC_AI_PROVIDER").get("value");
        assertThat(provider == null || provider.toString().isEmpty()).isTrue();
    }

    @Test
    void allowedUsers_ExistsWithoutAnyValueInTheRepository() throws IOException {
        var allowed = envVars().get("LIFESYNC_AI_ALLOWED_USERS");

        // Sem valor no repositorio: a lista (ids de contas) so e preenchida no painel, e vazia = ninguem.
        assertThat(allowed).isNotNull();
        assertThat(allowed.get("sync")).isEqualTo(false);
        assertThat(allowed).doesNotContainKey("value");
    }

    @Test
    void apiKey_IsASecretFilledInTheDashboard() throws IOException {
        var key = envVars().get("LLM_API_KEY");

        assertThat(key).isNotNull();
        assertThat(key.get("sync")).isEqualTo(false);
        assertThat(key).doesNotContainKey("value");
    }
}
