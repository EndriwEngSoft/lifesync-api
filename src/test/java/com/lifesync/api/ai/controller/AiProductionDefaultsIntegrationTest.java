package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.support.ApiTestSupport;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O padrao de producao (modulo desligado, provider vazio, sem chave): a
 * aplicacao inteira sobe, nenhum LlmClient existe e o endpoint responde 503.
 * E' o cenario real de um deploy que ainda nao configurou o modulo.
 */
@SpringBootTest(properties = {"lifesync.ai.enabled=false", "lifesync.ai.provider=", "lifesync.ai.api-key="})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Transactional
class AiProductionDefaultsIntegrationTest extends ApiTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;

    @Test
    void noLlmClientExists() {
        assertThat(context.getBeansOfType(LlmClient.class)).isEmpty();
    }

    @Test
    void endpointAnswers503() throws Exception {
        User user = newUser("a");

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("AI feature is currently unavailable."));
    }
}
