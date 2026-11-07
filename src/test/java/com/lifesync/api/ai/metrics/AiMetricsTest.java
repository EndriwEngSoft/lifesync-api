package com.lifesync.api.ai.metrics;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.client.LlmResponse;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.dto.UserContextDTO.TaskContext;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.enums.ReferenceType;
import com.lifesync.api.ai.exception.AiDisabledException;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.exception.InvalidLlmOutputException;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.exception.LlmProviderException.Type;
import com.lifesync.api.ai.metrics.AiMetrics.Rejection;
import com.lifesync.api.ai.service.AiAvailability;
import com.lifesync.api.ai.service.AiQuotaService;
import com.lifesync.api.ai.service.AiRequestLogService;
import com.lifesync.api.ai.service.ContextBuilder;
import com.lifesync.api.ai.service.ContextBuilder.BuiltContext;
import com.lifesync.api.ai.service.ContextBuilder.Reference;
import com.lifesync.api.ai.service.WeeklyPlanService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Metricas do modulo de IA: o que e' contado e, principalmente, o que NUNCA
 * pode aparecer (texto do prompt, resposta do modelo, chave, id do usuario).
 */
class AiMetricsTest {

    private static final String SECRET_TITLE = "TITULO-SECRETO-DO-USUARIO";
    private static final String SECRET_MODEL_TEXT = "TEXTO-SECRETO-DO-MODELO";
    private static final String API_KEY = "chave-super-secreta-123";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiMetrics metrics = new AiMetrics(registry);

    private final AiAvailability availability = mock(AiAvailability.class);
    private final AiQuotaService quotaService = mock(AiQuotaService.class);
    private final ContextBuilder contextBuilder = mock(ContextBuilder.class);
    private final AiRequestLogService logService = mock(AiRequestLogService.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final UUID userId = UUID.randomUUID();

    private WeeklyPlanService service;

    @BeforeEach
    void setUp() {
        AiProperties props = new AiProperties(true, "gemini", API_KEY, "http://localhost", "gemini-test",
                Duration.ofSeconds(5), Duration.ofSeconds(5), 100, 5, 3, Duration.ZERO);
        service = new WeeklyPlanService(availability, quotaService, contextBuilder, logService, props,
                JsonMapper.builder().build(), Validation.buildDefaultValidatorFactory().getValidator(), metrics);
        when(availability.requireClient()).thenReturn(llmClient);

        UserContextDTO context = new UserContextDTO("2026-11-06", "FRIDAY", "America/Sao_Paulo",
                List.of(new TaskContext("T1", SECRET_TITLE, "HIGH", "PENDING", "2026-11-07", false)),
                List.of(), List.of());
        Map<String, Reference> refs = new LinkedHashMap<>();
        refs.put("T1", new Reference(ReferenceType.TASK, UUID.randomUUID()));
        when(contextBuilder.build(userId)).thenReturn(new BuiltContext(context, refs));
    }

    // --- o Micrometer em si ---

    @Test
    void recordCall_RegistersDurationCountAndTokens() {
        metrics.recordCall(AiFeature.WEEKLY_PLAN, "gemini", AiRequestStatus.SUCCESS, 900, 120, 80);

        var timer = registry.get(AiMetrics.REQUEST_DURATION).tag("status", "SUCCESS").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(900.0);
        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "input").counter().count()).isEqualTo(120.0);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "output").counter().count()).isEqualTo(80.0);
    }

    @Test
    void recordCall_CountsPerStatusSeparately() {
        metrics.recordCall(AiFeature.WEEKLY_PLAN, "gemini", AiRequestStatus.SUCCESS, 100, 10, 10);
        metrics.recordCall(AiFeature.WEEKLY_PLAN, "gemini", AiRequestStatus.SUCCESS, 100, 10, 10);
        metrics.recordCall(AiFeature.WEEKLY_PLAN, "gemini", AiRequestStatus.TIMEOUT, 100, 0, 0);

        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "SUCCESS").counter().count()).isEqualTo(2.0);
        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "TIMEOUT").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "input").counter().count()).isEqualTo(20.0);
    }

    @Test
    void recordCall_WithoutTokens_DoesNotCreateTokenCounters() {
        metrics.recordCall(AiFeature.WEEKLY_PLAN, "gemini", AiRequestStatus.PROVIDER_ERROR, 50, 0, 0);

        assertThat(registry.find(AiMetrics.TOKENS).counters()).isEmpty();
        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "PROVIDER_ERROR").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordCall_BlankProvider_UsesUnknownAndNegativeLatencyIsZero() {
        metrics.recordCall(AiFeature.WEEKLY_PLAN, " ", AiRequestStatus.SUCCESS, -5, 1, 1);

        var timer = registry.get(AiMetrics.REQUEST_DURATION).tag("provider", "unknown").timer();
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isZero();
    }

    // --- o servico alimenta as metricas ---

    @Test
    void service_Success_RecordsCallAndTokens() {
        llmReturns("{\"summary\":\"ok\",\"priorities\":[{\"type\":\"TASK\",\"referenceId\":\"T1\",\"reason\":\"x\"}],"
                + "\"risks\":[],\"schedule\":{}}");

        service.generate(userId);

        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiMetrics.REQUEST_DURATION).tag("provider", "gemini").timer().count()).isEqualTo(1);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "input").counter().count()).isEqualTo(120.0);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "output").counter().count()).isEqualTo(80.0);
    }

    @Test
    void service_InvalidOutput_RecordsStatusAndTheTokensSpent() {
        llmReturns("isto nao e json");

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);

        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "INVALID_OUTPUT").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "output").counter().count()).isEqualTo(80.0);
    }

    @Test
    void service_ProviderError_RecordsStatusWithoutTokens() {
        when(llmClient.generatePlan(any())).thenThrow(new LlmProviderException(Type.SERVER_ERROR, "falhou"));

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(LlmProviderException.class);

        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "PROVIDER_ERROR").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find(AiMetrics.TOKENS).counters()).isEmpty();
    }

    @Test
    void service_Timeout_RecordsTimeoutStatus() {
        when(llmClient.generatePlan(any())).thenThrow(new LlmProviderException(Type.TIMEOUT, "demorou"));

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(LlmProviderException.class);

        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "TIMEOUT").counter().count()).isEqualTo(1.0);
    }

    @Test
    void service_QuotaExceeded_CountsARejectionAndNeverCallsTheProvider() {
        doThrow(new AiQuotaExceededException("cota")).when(quotaService).assertWithinQuota(any(), any());

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(AiQuotaExceededException.class);

        assertThat(registry.get(AiMetrics.REJECTIONS).tag("reason", Rejection.QUOTA_EXCEEDED.name()).counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.find(AiMetrics.REQUESTS).counters()).isEmpty();
    }

    @Test
    void service_Disabled_CountsARejection() {
        when(availability.requireClient()).thenThrow(new AiDisabledException("desligado"));

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(AiDisabledException.class);

        assertThat(registry.get(AiMetrics.REJECTIONS).tag("reason", Rejection.DISABLED.name()).counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void service_MetricsAreRecordedEvenIfWritingTheLogFails() {
        llmReturns("{\"summary\":\"ok\",\"priorities\":[],\"risks\":[],\"schedule\":{}}");
        doThrow(new IllegalStateException("banco fora")).when(logService)
                .record(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), any());

        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get(AiMetrics.REQUESTS).tag("status", "SUCCESS").counter().count()).isEqualTo(1.0);
    }

    // --- privacidade ---

    @Test
    void metrics_NeverContainPromptResponseKeyOrUserId() {
        llmReturns("{\"summary\":\"" + SECRET_MODEL_TEXT + "\",\"priorities\":[],\"risks\":[],\"schedule\":{}}");
        service.generate(userId);
        llmReturns("{\"resposta\":\"" + SECRET_MODEL_TEXT + "\" nao e json");
        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(InvalidLlmOutputException.class);
        when(llmClient.generatePlan(any())).thenThrow(new LlmProviderException(Type.SERVER_ERROR, "falhou"));
        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(LlmProviderException.class);

        assertThat(registry.getMeters()).isNotEmpty();
        for (Meter meter : registry.getMeters()) {
            StringBuilder everything = new StringBuilder(meter.getId().getName()).append(' ')
                    .append(meter.getId().getDescription()).append(' ');
            meter.getId().getTags().forEach(t -> everything.append(t.getKey()).append('=').append(t.getValue())
                    .append(' '));
            assertThat(everything.toString())
                    .doesNotContain(SECRET_TITLE, SECRET_MODEL_TEXT, API_KEY, userId.toString(), "user_data");
        }
    }

    @Test
    void metrics_UseOnlyAllowedTagKeys() {
        llmReturns("{\"summary\":\"ok\",\"priorities\":[],\"risks\":[],\"schedule\":{}}");
        service.generate(userId);
        when(llmClient.generatePlan(any())).thenThrow(new LlmProviderException(Type.TIMEOUT, "demorou"));
        assertThatThrownBy(() -> service.generate(userId)).isInstanceOf(LlmProviderException.class);

        registry.getMeters().forEach(m -> assertThat(m.getId().getTags())
                .extracting(t -> t.getKey())
                .isSubsetOf("feature", "provider", "status", "direction", "reason"));
    }

    private void llmReturns(String content) {
        when(llmClient.generatePlan(any())).thenReturn(new LlmResponse(content, 120, 80, "gemini-test", 900));
    }
}
