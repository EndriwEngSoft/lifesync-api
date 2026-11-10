package com.lifesync.api.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O curinga "*" da lista de permitidos existe para desenvolvimento e testes.
 * Em producao (perfil prod) com o provedor real ele abriria o modulo a todos
 * os usuarios, entao a aplicacao nao sobe.
 */
class AiAllowListGuardTest {

    private static final String DEMO = "0b9d6f0e-6a55-4b0c-9a55-0d7f0a1c2e3f";

    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    @Import(AiAllowListGuard.class)
    static class GuardConfig {
    }

    private final ApplicationContextRunner gemini = new ApplicationContextRunner()
            .withUserConfiguration(GuardConfig.class)
            .withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=gemini",
                    "lifesync.ai.api-key=chave-de-teste");

    private static String allMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void wildcardWithGeminiInProduction_FailsStartupWithAClearMessage() {
        gemini.withPropertyValues("spring.profiles.active=prod", "lifesync.ai.allowed-users=*").run(context -> {
            assertThat(context).hasFailed();
            assertThat(allMessages(context.getStartupFailure()))
                    .contains("LIFESYNC_AI_ALLOWED_USERS", "*", "gemini")
                    .doesNotContain("chave-de-teste");
        });
    }

    @Test
    void wildcardMixedWithIdsInProduction_StillFails() {
        gemini.withPropertyValues("spring.profiles.active=prod", "lifesync.ai.allowed-users=" + DEMO + ",*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void wildcardWithGeminiInProduction_FailsEvenWithTheModuleDisabled() {
        // Desligado hoje, ligado amanha por uma variavel: a combinacao nunca e' valida em producao.
        new ApplicationContextRunner().withUserConfiguration(GuardConfig.class)
                .withPropertyValues("spring.profiles.active=prod", "lifesync.ai.enabled=false",
                        "lifesync.ai.provider=gemini", "lifesync.ai.allowed-users=*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void listOfIdsOrEmptyListWithGeminiInProduction_Starts() {
        gemini.withPropertyValues("spring.profiles.active=prod", "lifesync.ai.allowed-users=" + DEMO)
                .run(context -> assertThat(context).hasNotFailed());
        gemini.withPropertyValues("spring.profiles.active=prod", "lifesync.ai.allowed-users=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void wildcardWithGeminiOutsideProduction_Starts() {
        // Avaliacao live: provedor real, dados sinteticos, usuarios criados na hora, sem o perfil prod.
        gemini.withPropertyValues("lifesync.ai.allowed-users=*").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiProperties.class).allowsUser(java.util.UUID.randomUUID())).isTrue();
        });
    }

    @Test
    void wildcardWithTheFakeProvider_Starts() {
        new ApplicationContextRunner().withUserConfiguration(GuardConfig.class)
                .withPropertyValues("lifesync.ai.enabled=true", "lifesync.ai.provider=fake",
                        "lifesync.ai.allowed-users=*")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
