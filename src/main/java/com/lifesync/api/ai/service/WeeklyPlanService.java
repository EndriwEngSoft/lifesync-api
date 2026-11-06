package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.client.LlmRequest;
import com.lifesync.api.ai.client.LlmResponse;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.dto.LlmWeeklyPlanDTO;
import com.lifesync.api.ai.dto.LlmWeeklyPlanDTO.LlmPriority;
import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO;
import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO.PrioritizedItemDTO;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.exception.InvalidLlmOutputException;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.prompt.SystemPrompt;
import com.lifesync.api.ai.prompt.WeeklyPlanSchema;
import com.lifesync.api.ai.service.ContextBuilder.BuiltContext;
import com.lifesync.api.ai.service.ContextBuilder.Reference;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gera o plano semanal do usuario autenticado.
 *
 * Fluxo: modulo ligado? -> cota diaria -> contexto minimizado (sem dados, nao
 * chama o LLM) -> chamada ao LLM -> parse -> Bean Validation -> indices
 * existentes -> troca dos indices pelos ids reais -> log da chamada.
 *
 * A saida do modelo e' entrada nao confiavel: so vira resposta depois de
 * passar por todas as validacoes. Cada chamada ao LLM deixa uma linha de log
 * (SUCCESS, PROVIDER_ERROR, INVALID_OUTPUT ou TIMEOUT) so com metadados.
 *
 * Este metodo nao e transacional de proposito: nao se segura uma transacao
 * (e uma conexao do pool) durante uma chamada de rede que pode levar segundos.
 */
@Slf4j
@Service
public class WeeklyPlanService {

    private static final Pattern INDEX_TOKEN = Pattern.compile("\\b([THG][0-9]{1,3})\\b");
    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    static final String EMPTY_PLAN_SUMMARY =
            "Você ainda não tem tarefas em aberto, hábitos ativos nem metas em andamento. "
                    + "Cadastre alguns itens para receber um plano semanal.";

    private final AiAvailability availability;
    private final AiQuotaService quotaService;
    private final ContextBuilder contextBuilder;
    private final AiRequestLogService logService;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public WeeklyPlanService(AiAvailability availability, AiQuotaService quotaService,
                             ContextBuilder contextBuilder, AiRequestLogService logService,
                             AiProperties properties, ObjectMapper objectMapper, Validator validator) {
        this.availability = availability;
        this.quotaService = quotaService;
        this.contextBuilder = contextBuilder;
        this.logService = logService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public WeeklyPlanResponseDTO generate(UUID userId) {
        LlmClient client = availability.requireClient();          // 503 se desligado
        quotaService.assertWithinQuota(userId, AiFeature.WEEKLY_PLAN); // 429 se acabou a cota

        BuiltContext built = contextBuilder.build(userId);
        if (built.context().isEmpty()) {
            return emptyPlan(); // nada a planejar: nao gasta chamada nem cota
        }

        LlmRequest request = new LlmRequest(SystemPrompt.WEEKLY_PLAN_SYSTEM_MESSAGE,
                objectMapper.writeValueAsString(built.context()), WeeklyPlanSchema.schema());

        long start = System.nanoTime();
        LlmResponse response;
        try {
            response = client.generatePlan(request);
        } catch (LlmProviderException e) {
            AiRequestStatus status = e.getType() == LlmProviderException.Type.TIMEOUT
                    ? AiRequestStatus.TIMEOUT : AiRequestStatus.PROVIDER_ERROR;
            record(userId, properties.model(), 0, 0, elapsedMs(start), status);
            throw e;
        }

        try {
            WeeklyPlanResponseDTO plan = toApiPlan(parseAndValidate(response.content()), built);
            record(userId, response.modelName(), response.inputTokens(), response.outputTokens(),
                    response.latencyMs(), AiRequestStatus.SUCCESS);
            return plan;
        } catch (InvalidLlmOutputException e) {
            record(userId, response.modelName(), response.inputTokens(), response.outputTokens(),
                    response.latencyMs(), AiRequestStatus.INVALID_OUTPUT);
            throw e;
        }
    }

    // --- parse e validacao da saida do modelo ---

    private LlmWeeklyPlanDTO parseAndValidate(String content) {
        LlmWeeklyPlanDTO dto;
        try {
            dto = objectMapper.readValue(stripCodeFence(content), LlmWeeklyPlanDTO.class);
        } catch (JacksonException e) {
            throw new InvalidLlmOutputException("LLM output is not valid JSON for the expected format.", e);
        }
        if (dto == null) {
            throw new InvalidLlmOutputException("LLM output is empty.");
        }
        Set<ConstraintViolation<LlmWeeklyPlanDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            // So os caminhos dos campos: o valor invalido e' texto do modelo e nao vai para mensagem/log.
            String fields = violations.stream().map(v -> v.getPropertyPath().toString()).sorted().distinct()
                    .toList().toString();
            throw new InvalidLlmOutputException("LLM output violates the expected format: " + fields);
        }
        return dto;
    }

    private static String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }
        Matcher matcher = CODE_FENCE.matcher(content);
        return matcher.matches() ? matcher.group(1) : content;
    }

    // --- troca dos indices pelos ids reais ---

    private WeeklyPlanResponseDTO toApiPlan(LlmWeeklyPlanDTO dto, BuiltContext built) {
        Map<String, Reference> references = built.references();
        Map<String, String> titles = titlesByIndex(built.context());

        List<PrioritizedItemDTO> priorities = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LlmPriority p : dto.priorities()) {
            Reference ref = references.get(p.referenceId());
            if (ref == null) {
                throw new InvalidLlmOutputException("LLM referenced an item that does not exist.");
            }
            if (ref.type() != p.type()) {
                throw new InvalidLlmOutputException("LLM item type does not match the referenced item.");
            }
            if (seen.add(p.referenceId())) { // repeticao do mesmo item e' descartada
                priorities.add(new PrioritizedItemDTO(ref.type(), ref.id(), humanize(p.reason(), titles)));
            }
        }

        List<String> risks = dto.risks().stream().map(r -> humanize(r, titles)).toList();

        Map<DayOfWeek, List<String>> schedule = new EnumMap<>(DayOfWeek.class);
        dto.schedule().forEach((day, items) ->
                schedule.put(day, items.stream().map(s -> humanize(s, titles)).toList()));

        return new WeeklyPlanResponseDTO(humanize(dto.summary(), titles), priorities, risks, schedule);
    }

    private static Map<String, String> titlesByIndex(UserContextDTO context) {
        Map<String, String> titles = new HashMap<>();
        context.tasks().forEach(t -> titles.put(t.shortIndex(), t.title()));
        context.habits().forEach(h -> titles.put(h.shortIndex(), h.name()));
        context.goals().forEach(g -> titles.put(g.shortIndex(), g.title()));
        return titles;
    }

    /**
     * Se o modelo citar um indice (T1) num texto livre, troca pelo titulo do
     * item: o cliente nao conhece os indices. Indice que nao existe fica como esta.
     */
    private static String humanize(String text, Map<String, String> titlesByIndex) {
        Matcher matcher = INDEX_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String title = titlesByIndex.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(title == null ? matcher.group(1)
                    : "\"" + title + "\""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static WeeklyPlanResponseDTO emptyPlan() {
        return new WeeklyPlanResponseDTO(EMPTY_PLAN_SUMMARY, List.of(), List.of(), new EnumMap<>(DayOfWeek.class));
    }

    // --- log ---

    private void record(UUID userId, String model, int inputTokens, int outputTokens, long latencyMs,
                        AiRequestStatus status) {
        logService.record(userId, AiFeature.WEEKLY_PLAN, SystemPrompt.PROMPT_VERSION, properties.provider(),
                model, inputTokens, outputTokens, latencyMs, status);
        log.info("AI call finished: feature={}, status={}, latencyMs={}", AiFeature.WEEKLY_PLAN, status, latencyMs);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
