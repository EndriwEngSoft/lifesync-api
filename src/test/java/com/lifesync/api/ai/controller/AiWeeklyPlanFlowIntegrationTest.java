package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.entity.AiRequestLog;
import com.lifesync.api.ai.enums.AiRequestStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo (JWT, controller, servico, contexto, cliente fake, banco e
 * cota) sem mocks do dominio. A cota diaria esta em 1 para provar o 429.
 */
@SpringBootTest(properties = {"lifesync.ai.enabled=true", "lifesync.ai.provider=fake",
        "lifesync.ai.api-key=", "lifesync.ai.daily-quota=1"})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Transactional
class AiWeeklyPlanFlowIntegrationTest extends ApiTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private AiRequestLogRepository logRepository;

    private Task task(User owner, String title) {
        return taskRepository.saveAndFlush(Task.builder().title(title).priority(Priority.HIGH)
                .status(Status.PENDING).user(owner).build());
    }

    @Test
    void userWithData_GetsAPlanWithTheirOwnTaskId_AndTheCallIsLogged() throws Exception {
        User user = newUser("a");
        Task mine = task(user, "minha-tarefa");

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorities[0].type").value("TASK"))
                .andExpect(jsonPath("$.priorities[0].id").value(mine.getId().toString()))
                // O indice curto nunca vaza para o cliente
                .andExpect(content().string(not(containsString("\"T1\""))));

        List<AiRequestLog> logs = logRepository.findAll().stream().filter(l -> l.getUserId().equals(user.getId()))
                .toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getStatus()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(logs.getFirst().getProvider()).isEqualTo("fake");
    }

    @Test
    void planOfUserA_NeverReferencesItemsOfUserB() throws Exception {
        User userA = newUser("a");
        User userB = newUser("b");
        Task mine = task(userA, "tarefa-do-a");
        Task theirs = task(userB, "tarefa-do-b");

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorities[0].id").value(mine.getId().toString()))
                .andExpect(content().string(not(containsString(theirs.getId().toString()))));
    }

    @Test
    void secondCallOfTheDay_Returns429AndNeverReachesTheProviderAgain() throws Exception {
        User user = newUser("a");
        task(user, "tarefa");

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));

        assertThat(logRepository.findAll().stream().filter(l -> l.getUserId().equals(user.getId())).count())
                .isEqualTo(1);
    }

    @Test
    void quotaOfOneUserDoesNotBlockAnother() throws Exception {
        User heavy = newUser("heavy");
        User light = newUser("light");
        task(heavy, "t1");
        task(light, "t2");

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(heavy))).andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(heavy)))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(light))).andExpect(status().isOk());
    }

    @Test
    void userWithoutData_GetsAnEmptyPlan_AndSpendsNoQuota() throws Exception {
        User user = newUser("vazio");

        for (int i = 0; i < 2; i++) { // cota = 1, mas sem dados nada e' consumido
            mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priorities", hasSize(0)));
        }

        assertThat(logRepository.findAll().stream().filter(l -> l.getUserId().equals(user.getId())).count())
                .isZero();
    }
}
