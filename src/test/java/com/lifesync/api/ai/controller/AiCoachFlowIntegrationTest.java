package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.client.LlmToolRequest;
import com.lifesync.api.ai.client.LlmToolResponse;
import com.lifesync.api.ai.client.ToolCall;
import com.lifesync.api.ai.entity.AiRequestLog;
import com.lifesync.api.ai.enums.AiFeature;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
 * Fluxo completo do assistente com JWT real e PostgreSQL: o modelo e' um mock
 * que pede ferramentas, e as ferramentas rodam de verdade sobre o banco. Prova
 * que o que volta ao modelo e' sempre do usuario do token.
 */
@SpringBootTest(properties = {"lifesync.ai.enabled=true", "lifesync.ai.provider=fake", "lifesync.ai.api-key=",
        "lifesync.ai.daily-quota=1"})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Transactional
class AiCoachFlowIntegrationTest extends ApiTestSupport {

    private static final String BODY = "{\"question\":\"O que esta atrasado?\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private AiRequestLogRepository logRepository;

    @MockitoBean
    private LlmClient llmClient;

    private void overdueTask(User owner, String title) {
        taskRepository.saveAndFlush(Task.builder().title(title).priority(Priority.HIGH).status(Status.PENDING)
                .dueDate(LocalDate.now().minusDays(10)).user(owner).build());
    }

    private static LlmToolResponse wantsTool(String name, Map<String, Object> args) {
        return new LlmToolResponse("", List.of(new ToolCall("call-1", name, args)),
                Map.of("role", "model"), false, 100, 10, "fake", 5);
    }

    private static LlmToolResponse answers(String text) {
        return new LlmToolResponse(text, List.of(), null, false, 150, 30, "fake", 5);
    }

    private List<AiRequestLog> logsOf(User user) {
        return logRepository.findAll().stream().filter(l -> l.getUserId().equals(user.getId())).toList();
    }

    @Test
    void userA_NeverGetsDataFromUserB_EvenWhenTheModelAsksForEverything() throws Exception {
        User a = newUser("a");
        User b = newUser("b");
        overdueTask(a, "Tarefa secreta da Ana");
        overdueTask(b, "Tarefa secreta do Bruno");
        when(llmClient.chat(any()))
                .thenReturn(wantsTool("tarefas_atrasadas", Map.of("limite", 20L)))
                .thenReturn(answers("Voce tem uma tarefa atrasada."));

        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Voce tem uma tarefa atrasada."))
                .andExpect(jsonPath("$.toolsUsed[0]").value("tarefas_atrasadas"))
                .andExpect(jsonPath("$.steps").value(2));

        ArgumentCaptor<LlmToolRequest> captor = ArgumentCaptor.forClass(LlmToolRequest.class);
        verify(llmClient, times(2)).chat(captor.capture());
        String sentToTheModel = captor.getAllValues().toString();
        assertThat(sentToTheModel).contains("Tarefa secreta da Ana");
        assertThat(sentToTheModel).doesNotContain("Bruno", a.getId().toString(), b.getId().toString());

        assertThat(logsOf(a)).extracting(AiRequestLog::getStatus)
                .containsExactlyInAnyOrder(AiRequestStatus.TOOL_STEP, AiRequestStatus.SUCCESS);
        assertThat(logsOf(a)).allSatisfy(l -> assertThat(l.getFeature()).isEqualTo(AiFeature.COACH));
        assertThat(logsOf(b)).isEmpty();
    }

    @Test
    void modelAskingForAnotherUsersId_Gets502AndNoToolRuns() throws Exception {
        User a = newUser("a");
        User b = newUser("b");
        overdueTask(a, "Tarefa da Ana");
        overdueTask(b, "Tarefa do Bruno");
        when(llmClient.chat(any())).thenReturn(
                wantsTool("tarefas_atrasadas", Map.of("userId", b.getId().toString())));

        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("The AI assistant could not complete the request."));

        verify(llmClient, times(1)).chat(any());
        assertThat(logsOf(a)).extracting(AiRequestLog::getStatus).containsExactly(AiRequestStatus.INVALID_OUTPUT);
    }

    @Test
    void answeredQuestionConsumesTheQuota_AndTheSecondOneIs429() throws Exception {
        User a = newUser("a");
        overdueTask(a, "Tarefa da Ana");
        when(llmClient.chat(any())).thenReturn(answers("Resposta."));

        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isTooManyRequests());

        verify(llmClient, times(1)).chat(any());
    }

    @Test
    void withoutToken_Is401() throws Exception {
        mockMvc.perform(post("/api/ai/coach").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(llmClient);
    }

    @Test
    void blankOversizedOrMissingQuestion_Is400WithoutCallingTheModel() throws Exception {
        User a = newUser("a");

        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "a".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ai/coach").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(llmClient);
    }
}
