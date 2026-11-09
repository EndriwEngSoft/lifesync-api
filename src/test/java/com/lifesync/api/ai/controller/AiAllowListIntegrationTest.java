package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.client.LlmResponse;
import com.lifesync.api.ai.repository.AiRequestLogRepository;
import com.lifesync.api.support.ApiTestSupport;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lista de permitidos (lifesync.ai.allowed-users): com o modulo ligado, so o
 * usuario cujo id esta na lista usa a IA. Quem esta fora recebe o mesmo 503 do
 * modulo desligado, e nada do provedor e' chamado nem registrado.
 *
 * A lista precisa existir antes de o contexto subir, entao o usuario permitido
 * e' inserido com um id fixo (os ids normais sao gerados na gravacao).
 */
@SpringBootTest(properties = {"lifesync.ai.enabled=true", "lifesync.ai.provider=fake",
        "lifesync.ai.allowed-users=" + AiAllowListIntegrationTest.ALLOWED_ID})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Transactional
class AiAllowListIntegrationTest extends ApiTestSupport {

    static final String ALLOWED_ID = "5f0c1f6e-2b7a-4a55-8f43-7f6f0f3f9a11";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private AiRequestLogRepository logRepository;

    @MockitoBean
    private LlmClient llmClient;

    private User allowedUser() {
        UUID id = UUID.fromString(ALLOWED_ID);
        jdbc.update("INSERT INTO tb_users (id, name, username, email, password_hash, role, active, timezone) "
                        + "VALUES (?, 'Conta demo', ?, ?, 'hash', 'USER', true, 'America/Sao_Paulo')",
                id, "demo-" + id, "demo-" + id + "@teste.com");
        return userRepository.findById(id).orElseThrow();
    }

    private void task(User owner) {
        taskRepository.saveAndFlush(Task.builder().title("Pagar a conta de luz").priority(Priority.HIGH)
                .status(Status.PENDING).user(owner).build());
    }

    private long logsOf(User user) {
        return logRepository.findAll().stream().filter(l -> l.getUserId().equals(user.getId())).count();
    }

    @Test
    void userOutsideTheList_Gets503WithoutCallingTheProviderOrWritingALog() throws Exception {
        User outsider = newUser("fora");
        task(outsider);

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(outsider)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI feature is currently unavailable."));

        verifyNoInteractions(llmClient);
        assertThat(logsOf(outsider)).isZero();
    }

    @Test
    void userInTheList_GetsTheirPlan() throws Exception {
        User demo = allowedUser();
        task(demo);
        when(llmClient.generatePlan(any())).thenReturn(new LlmResponse("""
                {"summary":"Semana tranquila.",
                 "priorities":[{"type":"TASK","referenceId":"T1","reason":"Vence logo."}],
                 "risks":[],"schedule":{}}
                """, 100, 50, "fake", 10));

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(demo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Semana tranquila."))
                .andExpect(jsonPath("$.priorities[0].type").value("TASK"));

        verify(llmClient, times(1)).generatePlan(any());
        assertThat(logsOf(demo)).isEqualTo(1);
    }
}
