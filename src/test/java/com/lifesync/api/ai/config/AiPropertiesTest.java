package com.lifesync.api.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valores padrao, validacao que falha na subida e protecao da chave. Usa
 * ApplicationContextRunner: sem banco, sem rede, rapido.
 */
class AiPropertiesTest {

    private static final String SECRET = "chave-secreta-de-teste-123";

    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    static class PropsConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class);

    @Test
    void withoutAnyProperty_UsesSafeDefaults() {
        runner.run(context -> {
            AiProperties props = context.getBean(AiProperties.class);
            assertThat(props.enabled()).isFalse();
            assertThat(props.provider()).isEmpty();
            assertThat(props.apiKey()).isEmpty();
            assertThat(props.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com");
            assertThat(props.model()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(props.maxTokens()).isEqualTo(2048);
            assertThat(props.dailyQuota()).isEqualTo(5);
            assertThat(props.maxAttempts()).isEqualTo(3);
        });
    }

    @Test
    void enabledWithGeminiAndNoKey_FailsStartup() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=gemini").run(context -> {
            assertThat(context).hasFailed();
            assertThat(allMessages(context.getStartupFailure())).contains("lifesync.ai.api-key");
        });
    }

    @Test
    void enabledWithGeminiAndBlankKey_FailsStartup() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=gemini",
                "lifesync.ai.api-key=   ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledWithoutProvider_FailsStartup() {
        runner.withPropertyValues("lifesync.ai.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(allMessages(context.getStartupFailure())).contains("lifesync.ai.provider");
        });
    }

    @Test
    void enabledWithUnknownProvider_FailsStartup() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=outro")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledWithFakeProvider_StartsWithoutKey() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=fake")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void disabledWithGeminiAndNoKey_Starts() {
        runner.withPropertyValues("lifesync.ai.enabled=false", "lifesync.ai.provider=gemini")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void providerIsNormalizedToLowerCase() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider= FAKE ")
                .run(context -> assertThat(context.getBean(AiProperties.class).provider()).isEqualTo("fake"));
    }

    @Test
    void invalidNumbersAndTimeouts_FailStartup() {
        runner.withPropertyValues("lifesync.ai.read-timeout=0s").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("lifesync.ai.connect-timeout=-1s").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("lifesync.ai.max-tokens=0").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("lifesync.ai.daily-quota=0").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("lifesync.ai.max-attempts=9").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("lifesync.ai.retry-backoff=-5ms").run(c -> assertThat(c).hasFailed());
    }

    @Test
    void toString_NeverContainsTheKey() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=gemini",
                "lifesync.ai.api-key=" + SECRET).run(context -> {
            AiProperties props = context.getBean(AiProperties.class);
            assertThat(props.apiKey()).isEqualTo(SECRET);
            assertThat(props.toString()).doesNotContain(SECRET).contains("<masked>");
        });
    }

    @Test
    void failureMessageNeverContainsTheKey() {
        runner.withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=gemini",
                "lifesync.ai.api-key=" + SECRET, "lifesync.ai.read-timeout=0s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(allMessages(context.getStartupFailure())).doesNotContain(SECRET);
        });
    }

    // --- Os yml reais: dev liga o fake; producao nasce desligada e sem provider ---

    @Test
    void devYaml_EnablesFakeProvider() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("lifesync.ai.api-key=").run(context -> {
                    AiProperties props = context.getBean(AiProperties.class);
                    assertThat(props.enabled()).isTrue();
                    assertThat(props.provider()).isEqualTo("fake");
                });
    }

    @Test
    void prodYaml_StartsDisabledWithoutProvider() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    AiProperties props = context.getBean(AiProperties.class);
                    assertThat(props.enabled()).isFalse();
                    assertThat(props.provider()).isEmpty();
                });
    }

    private static String allMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
