package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.client.LlmRequest;
import com.lifesync.api.ai.client.LlmResponse;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.dto.UserContextDTO.GoalContext;
import com.lifesync.api.ai.dto.UserContextDTO.HabitContext;
import com.lifesync.api.ai.dto.UserContextDTO.TaskContext;
import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.enums.ReferenceType;
import com.lifesync.api.ai.exception.AiDisabledException;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.exception.InvalidLlmOutputException;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.exception.LlmProviderException.Type;
import com.lifesync.api.ai.prompt.SystemPrompt;
import com.lifesync.api.ai.service.ContextBuilder.BuiltContext;
import com.lifesync.api.ai.service.ContextBuilder.Reference;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Servico do plano semanal com LlmClient mockado: nenhuma chamada real a LLM.
 */
class WeeklyPlanServiceTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final AiAvailability availability = mock(AiAvailability.class);
    private final AiQuotaService quotaService = mock(AiQuotaService.class);
    private final ContextBuilder contextBuilder = mock(ContextBuilder.class);
    private final AiRequestLogService logService = mock(AiRequestLogService.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private WeeklyPlanService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();
    private final UUID habitId = UUID.randomUUID();
    private final UUID goalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AiProperties props = new AiProperties(true, "gemini", "chave-de-teste", "http://localhost", "gemini-test",
                Duration.ofSeconds(5), Duration.ofSeconds(5), 100, 5, 3, Duration.ZERO);
        service = new WeeklyPlanService(availability, quotaService, contextBuilder, logService, props,
                objectMapper, validator, new com.lifesync.api.ai.metrics.AiMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        when(availability.requireClient()).thenReturn(llmClient);
        when(contextBuilder.build(userId)).thenReturn(contextWithData());
    }

    private BuiltContext contextWithData() {
        UserContextDTO context = new UserContextDTO("2026-11-06", "FRIDAY", "America/Sao_Paulo",
                List.of(new TaskContext("T1", "Pagar a conta de luz", "HIGH", "PENDING", "2026-11-07", false)),
                List.of(new HabitContext("H1", "Meditar", "DAILY", 4, "2026-11-05", false, true)),
                List.of(new GoalContext("G1", "Juntar dinheiro", "IN_PROGRESS", 25.0, "2026-12-31")));
        Map<String, Reference> refs = new LinkedHashMap<>();
        refs.put("T1", new Reference(ReferenceType.TASK, taskId));
        refs.put("H1", new Reference(ReferenceType.HABIT, habitId));
        refs.put("G1", new Reference(ReferenceType.GOAL, goalId));
        return new BuiltContext(context, refs);
    }

    private static final String VALID_PLAN = """
            {"summary":"Semana focada em contas e habitos.",
             "priorities":[
               {"type":"TASK","referenceId":"T1","reason":"Vence amanha."},
               {"type":"HABIT","referenceId":"H1","reason":"Evita perder a sequencia."}],
             "risks":["Sequencia de H1 em risco."],
             "schedule":{"MONDAY":["Fazer T1 cedo."],"FRIDAY":["Revisar a semana."]}}
            """;

    private void llmReturns(String content) {
        when(llmClient.generatePlan(any())).thenReturn(new LlmResponse(content, 120, 80, "gemini-test", 900));
    }

    private void verifyLogged(AiRequestStatus status) {
        verify(logService).record(eq(userId), eq(AiFeature.WEEKLY_PLAN), eq(SystemPrompt.PROMPT_VERSION),
                eq("gemini"), anyString(), anyInt(), anyInt(), anyLong(), eq(status));
    }

    // --- sucesso ---

    @Test
    void success_MapsIndexesBackToRealIds_AndLogsSuccess() {
        llmReturns(VALID_PLAN);

        WeeklyPlanResponseDTO plan = service.generate(userId);

        assertThat(plan.summary()).isEqualTo("Semana focada em contas e habitos.");
        assertThat(plan.priorities()).hasSize(2);
        assertThat(plan.priorities().get(0).id()).isEqualTo(taskId);
        assertThat(plan.priorities().get(0).type()).isEqualTo(ReferenceType.TASK);
        assertThat(plan.priorities().get(1).id()).isEqualTo(habitId);
        assertThat(plan.schedule()).containsOnlyKeys(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        verify(logService).record(userId, AiFeature.WEEKLY_PLAN, SystemPrompt.PROMPT_VERSION, "gemini",
                "gemini-test", 120, 80, 900, AiRequestStatus.SUCCESS);
    }

    @Test
    void success_ReplacesIndexesInFreeTextWithTheItemTitle() {
        llmReturns(VALID_PLAN);

        WeeklyPlanResponseDTO plan = service.generate(userId);

        assertThat(plan.risks()).containsExactly("Sequencia de \"Meditar\" em risco.");
        assertThat(plan.schedule().get(DayOfWeek.MONDAY)).containsExactly("Fazer \"Pagar a conta de luz\" cedo.");
    }

    @Test
    void success_KeepsATitleQuotedByTheModelEvenWhenItLooksLikeAnIndex() {
        // O titulo da tarefa contem "G1" (nome de uma prova) e existe uma meta G1 no contexto.
        UserContextDTO context = new UserContextDTO("2026-11-06", "FRIDAY", "America/Sao_Paulo",
                List.of(new TaskContext("T1", "Estudar para a prova G1 de Calculo", "HIGH", "PENDING", null, false)),
                List.of(),
                List.of(new GoalContext("G1", "Juntar dinheiro", "IN_PROGRESS", 25.0, "2026-12-31")));
        Map<String, Reference> refs = new LinkedHashMap<>();
        refs.put("T1", new Reference(ReferenceType.TASK, taskId));
        refs.put("G1", new Reference(ReferenceType.GOAL, goalId));
        when(contextBuilder.build(userId)).thenReturn(new BuiltContext(context, refs));
        llmReturns("""
                {"summary":"Foque em Estudar para a prova G1 de Calculo e depois em G1.",
                 "priorities":[{"type":"TASK","referenceId":"T1","reason":"Prova proxima."}],
                 "risks":[],
                 "schedule":{}}
                """);

        WeeklyPlanResponseDTO plan = service.generate(userId);

        // Dentro do titulo citado o "G1" fica; o "G1" solto, que e' indice, vira o titulo da meta.
        assertThat(plan.summary())
                .isEqualTo("Foque em Estudar para a prova G1 de Calculo e depois em \"Juntar dinheiro\".");
    }

    @Test
    void success_RequestToTheLlmCarriesOnlyMinimizedContextAndTheSchema() {
        llmReturns(VALID_PLAN);

        service.generate(userId);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generatePlan(captor.capture());
        LlmRequest sent = captor.getValue();
        assertThat(sent.systemPrompt()).isEqualTo(SystemPrompt.WEEKLY_PLAN_SYSTEM_MESSAGE);
        assertThat(sent.responseSchema()).containsKey("properties");
        assertThat(sent.userContextJson()).contains("Pagar a conta de luz", "\"shortIndex\":\"T1\"");
        assertThat(UUID_PATTERN.matcher(sent.userContextJson()).find()).isFalse();
        assertThat(sent.userContextJson()).doesNotContain(taskId.toString(), userId.toString());
    }

    @Test
    void success_ChecksAvailabilityThenQuotaThenBuildsContext() {
        llmReturns(VALID_PLAN);

        service.generate(userId);

        InOrder order = inOrder(availability, quotaService, contextBuilder, llmClient);
        order.verify(availability).requireClient();
        order.verify(quotaService).assertWithinQuota(userId, AiFeature.WEEKLY_PLAN);
        order.verify(contextBuilder).build(userId);
        order.verify(llmClient).generatePlan(any());
    }

    @Test
    void success_AcceptsJsonWrappedInACodeFence() {
        llmReturns("```json\n" + VALID_PLAN + "\n```");

        assertThat(service.generate(userId).priorities()).hasSize(2);
    }

    @Test
    void success_DropsRepeatedItems() {
        llmReturns("""
                {"summary":"ok","priorities":[
                  {"type":"TASK","referenceId":"T1","reason":"a"},
                  {"type":"TASK","referenceId":"T1","reason":"b"}],
                 "risks":[],"schedule":{}}
                """);

        assertThat(service.generate(userId).priorities()).hasSize(1);
    }

    @Test
    void success_IgnoresUnknownFieldsFromTheModel() {
        llmReturns("""
                {"summary":"ok","priorities":[],"risks":[],"schedule":{},"campoExtra":"x"}
                """);

        assertThat(service.generate(userId).summary()).isEqualTo("ok");
    }

    @Test
    void fakeClient_ProducesAPlanThatPassesAllValidations() {
        when(llmClient.generatePlan(any()))
                .thenAnswer(inv -> new com.lifesync.api.ai.client.FakeLlmClient().generatePlan(inv.getArgument(0)));

        WeeklyPlanResponseDTO plan = service.generate(userId);

        assertThat(plan.priorities()).extracting(p -> p.id()).containsExactly(taskId, habitId, goalId);
    }

    // --- JSON invalido ---

    @ParameterizedTest
    @ValueSource(strings = {
            "isto nao e json",
            "{ \"summary\": ",
            "",                                                        // resposta cortada antes de qualquer texto
            "[]",
            "null",
            "{\"summary\":\"ok\"}",                                   // faltam campos obrigatorios
    })
    void invalidJsonOrShape_IsInvalidOutputAndLogged(String content) {
        llmReturns(content);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
        verifyLogged(AiRequestStatus.INVALID_OUTPUT);
    }

    @Test
    void invalidOutput_ErrorMessageNeverContainsTheModelText() {
        llmReturns("{\"summary\":\"SEGREDO-DO-MODELO\",\"priorities\":\"nao e lista\"}");

        assertThatThrownBy(() -> service.generate(userId))
                .isInstanceOf(InvalidLlmOutputException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("SEGREDO-DO-MODELO");
                    if (e.getCause() != null) {
                        assertThat(String.valueOf(e.getCause().getMessage())).doesNotContain("SEGREDO-DO-MODELO");
                    }
                });
    }

    @Test
    void invalidOutput_StillLogsTheTokensThatWereSpent() {
        llmReturns("nao e json");

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);

        verify(logService).record(userId, AiFeature.WEEKLY_PLAN, SystemPrompt.PROMPT_VERSION, "gemini",
                "gemini-test", 120, 80, 900, AiRequestStatus.INVALID_OUTPUT);
    }

    // --- Bean Validation ---

    @Test
    void blankSummary_IsInvalid() {
        llmReturns("{\"summary\":\"  \",\"priorities\":[],\"risks\":[],\"schedule\":{}}");

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void unknownType_IsInvalid() {
        llmReturns("""
                {"summary":"ok","priorities":[{"type":"PROJECT","referenceId":"T1","reason":"x"}],
                 "risks":[],"schedule":{}}
                """);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
        verifyLogged(AiRequestStatus.INVALID_OUTPUT);
    }

    @Test
    void malformedReferenceId_IsInvalid() {
        llmReturns("""
                {"summary":"ok","priorities":[{"type":"TASK","referenceId":"11111111-2222-3333-4444-555555555555",
                 "reason":"x"}],"risks":[],"schedule":{}}
                """);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining("referenceId");
    }

    @Test
    void unknownWeekday_IsInvalid() {
        llmReturns("""
                {"summary":"ok","priorities":[],"risks":[],"schedule":{"FUNDAY":["x"]}}
                """);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
    }

    @Test
    void oversizedFields_AreInvalid() {
        llmReturns("{\"summary\":\"" + "x".repeat(1001) + "\",\"priorities\":[],\"risks\":[],\"schedule\":{}}");

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
    }

    @Test
    void tooManyPriorities_IsInvalid() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 31; i++) {
            items.append(i == 0 ? "" : ",").append("{\"type\":\"TASK\",\"referenceId\":\"T1\",\"reason\":\"x\"}");
        }
        llmReturns("{\"summary\":\"ok\",\"priorities\":[" + items + "],\"risks\":[],\"schedule\":{}}");

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
    }

    @Test
    void blankScheduleItem_IsInvalid() {
        llmReturns("""
                {"summary":"ok","priorities":[],"risks":[],"schedule":{"MONDAY":[""]}}
                """);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
    }

    // --- indice inventado ---

    @Test
    void inventedIndex_IsRejected() {
        llmReturns("""
                {"summary":"ok","priorities":[{"type":"TASK","referenceId":"T9","reason":"x"}],
                 "risks":[],"schedule":{}}
                """);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining("does not exist");
        verifyLogged(AiRequestStatus.INVALID_OUTPUT);
    }

    @Test
    void typeThatDoesNotMatchTheIndex_IsRejected() {
        llmReturns("""
                {"summary":"ok","priorities":[{"type":"HABIT","referenceId":"T1","reason":"x"}],
                 "risks":[],"schedule":{}}
                """);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining("does not match");
    }

    // --- erro do provedor ---

    @ParameterizedTest
    @ValueSource(strings = {"RATE_LIMITED", "SERVER_ERROR", "CLIENT_ERROR", "EMPTY_RESPONSE", "BLOCKED",
            "INVALID_RESPONSE"})
    void providerError_IsRethrownAndLoggedAsProviderError(String type) {
        LlmProviderException failure = new LlmProviderException(Type.valueOf(type), "falha");
        when(llmClient.generatePlan(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.generate(userId)).isSameAs(failure);
        verifyLogged(AiRequestStatus.PROVIDER_ERROR);
    }

    @Test
    void providerTimeout_IsLoggedAsTimeout() {
        LlmProviderException failure = new LlmProviderException(Type.TIMEOUT, "demorou");
        when(llmClient.generatePlan(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.generate(userId)).isSameAs(failure);
        verifyLogged(AiRequestStatus.TIMEOUT);
    }

    // --- cota, modulo desligado e usuario sem dados ---

    @Test
    void quotaExceeded_DoesNotBuildContextNorCallTheLlmNorLog() {
        org.mockito.Mockito.doThrow(new AiQuotaExceededException("cota"))
                .when(quotaService).assertWithinQuota(userId, AiFeature.WEEKLY_PLAN);

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(AiQuotaExceededException.class);

        verifyNoInteractions(contextBuilder, llmClient, logService);
    }

    @Test
    void moduleDisabled_TouchesNothing() {
        when(availability.requireClient()).thenThrow(new AiDisabledException("desligado"));

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(AiDisabledException.class);

        verifyNoInteractions(quotaService, contextBuilder, llmClient, logService);
    }

    @Test
    void userWithoutData_ReturnsEmptyPlanWithoutCallingTheLlm() {
        UserContextDTO empty = new UserContextDTO("2026-11-06", "FRIDAY", "America/Sao_Paulo",
                List.of(), List.of(), List.of());
        when(contextBuilder.build(userId)).thenReturn(new BuiltContext(empty, Map.of()));

        WeeklyPlanResponseDTO plan = service.generate(userId);

        assertThat(plan.summary()).isEqualTo(WeeklyPlanService.EMPTY_PLAN_SUMMARY);
        assertThat(plan.priorities()).isEmpty();
        assertThat(plan.risks()).isEmpty();
        assertThat(plan.schedule()).isEmpty();
        verify(llmClient, never()).generatePlan(any());
        verifyNoInteractions(logService);
    }
}
