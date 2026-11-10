package com.lifesync.api.ai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava os padroes do application-prod.yml: sem nenhuma variavel de ambiente,
 * o modulo de IA fica desligado, sem provider, sem chave e sem ninguem na lista
 * de permitidos. E' isso que garante que um deploy nasce com a IA desligada,
 * ja que o render.yaml nao define valor para essas variaveis.
 *
 * O ambiente e montado so com o arquivo (sem variaveis do sistema), para o
 * resultado nao depender da maquina onde o teste roda.
 */
class ProductionAiDefaultsTest {

    private final StandardEnvironment environment = new StandardEnvironment();

    @BeforeEach
    void loadOnlyTheProductionFile() throws IOException {
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));
        sources.forEach(environment.getPropertySources()::addLast);
    }

    private String resolved(String property) {
        return environment.getProperty(property);
    }

    @Test
    void withoutEnvironmentVariables_TheModuleIsDisabled() {
        assertThat(resolved("lifesync.ai.enabled")).isEqualTo("false");
    }

    @Test
    void withoutEnvironmentVariables_ThereIsNoProviderKeyOrAllowedUser() {
        assertThat(resolved("lifesync.ai.provider")).isEmpty();
        assertThat(resolved("lifesync.ai.api-key")).isEmpty();
        assertThat(resolved("lifesync.ai.allowed-users")).isEmpty();
    }
}
