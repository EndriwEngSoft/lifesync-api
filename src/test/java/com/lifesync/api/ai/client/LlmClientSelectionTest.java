package com.lifesync.api.ai.client;

import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.config.GeminiRestClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que o LlmClient ativo depende so de lifesync.ai.provider. Sem
 * provider nao existe nenhum bean (falha fechada): em producao o Fake nunca
 * responde no lugar do provedor real.
 */
class LlmClientSelectionTest {

    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    static class PropsConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class, FakeLlmClient.class, GeminiLlmClient.class,
                    GeminiRestClientConfig.class)
            .withPropertyValues("lifesync.ai.base-url=http://localhost:1", "lifesync.ai.model=teste",
                    "lifesync.ai.api-key=chave-so-de-teste");

    @Test
    void providerFake_RegistersOnlyFakeClient() {
        runner.withPropertyValues("lifesync.ai.provider=fake").run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context).hasSingleBean(FakeLlmClient.class);
            assertThat(context).doesNotHaveBean(GeminiLlmClient.class);
        });
    }

    @Test
    void providerGemini_RegistersOnlyGeminiClient() {
        runner.withPropertyValues("lifesync.ai.provider=gemini").run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context).hasSingleBean(GeminiLlmClient.class);
            assertThat(context).doesNotHaveBean(FakeLlmClient.class);
        });
    }

    @Test
    void noProvider_RegistersNoClient() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void unknownProvider_RegistersNoClient() {
        runner.withPropertyValues("lifesync.ai.provider=outro").run(context ->
                assertThat(context).doesNotHaveBean(LlmClient.class));
    }
}
