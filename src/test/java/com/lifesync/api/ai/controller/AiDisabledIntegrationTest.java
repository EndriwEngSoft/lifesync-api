package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.repository.AiRequestLogRepository;
import com.lifesync.api.support.ApiTestSupport;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Com lifesync.ai.enabled=false o endpoint responde 503 no formato padrao e
 * nada do provedor e' chamado nem registrado. Um LlmClient mockado prova que
 * ele nunca e usado mesmo estando registrado.
 */
@SpringBootTest(properties = {"lifesync.ai.enabled=false", "lifesync.ai.provider=fake"})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Transactional
class AiDisabledIntegrationTest extends ApiTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiRequestLogRepository logRepository;

    @MockitoBean
    private LlmClient llmClient;

    @Test
    void disabledModule_Returns503WithoutCallingTheProvider() throws Exception {
        User user = newUser("a");

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI feature is currently unavailable."))
                .andExpect(jsonPath("$.path").value("/api/ai/weekly-plan"));

        verifyNoInteractions(llmClient);
        assertThat(logRepository.findAll().stream().filter(l -> l.getUserId().equals(user.getId())).count())
                .isZero();
    }

    @Test
    void disabledModule_StillRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai/weekly-plan")).andExpect(status().isUnauthorized());
    }
}
