package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.client.LlmToolRequest;
import com.lifesync.api.ai.client.LlmToolResponse;
import com.lifesync.api.ai.client.ToolCall;
import com.lifesync.api.ai.client.ToolExchange;
import com.lifesync.api.ai.client.ToolResult;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.dto.CoachResponseDTO;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.exception.AiDisabledException;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.exception.CoachAgentException;
import com.lifesync.api.ai.exception.CoachAgentException.Type;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.metrics.AiMetrics;
import com.lifesync.api.ai.metrics.AiMetrics.Rejection;
import com.lifesync.api.ai.prompt.CoachPrompt;
import com.lifesync.api.ai.service.ContextBuilder.BuiltContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Assistente de produtividade: responde a uma pergunta curta do usuario
 * consultando os dados dele por ferramentas somente leitura.
 *
 * O loop e' do servidor, nao do modelo. A cada passo o modelo ou responde em
 * texto ou pede ferramentas; o servico valida o pedido, executa a ferramenta
 * sobre o contexto do usuario do token e devolve o resultado. Travas:
 * - numero maximo de chamadas ao modelo (MAX_STEPS);
 * - numero maximo de ferramentas por passo;
 * - tempo total da conversa, conferido antes de cada chamada;
 * - ferramenta desconhecida ou argumento invalido encerram com erro tipado;
 * - a resposta final e' saida nao confiavel: vazia, cortada ou longa demais
 *   e' rejeitada.
 *
 * Cada chamada ao modelo deixa uma linha de log so com metadados. Passos
 * intermediarios (TOOL_STEP) nao consomem cota; a resposta final ou a falha
 * de validacao consomem, entao a cota conta perguntas, nao passos.
 *
 * Sem transacao: nao se segura uma conexao do banco durante chamadas de rede.
 */
@Slf4j
@Service
public class CoachService {

    /** Chamadas ao modelo por pergunta: ate 3 rodadas de ferramentas e a resposta. */
    static final int MAX_STEPS = 4;
    static final int MAX_TOOL_CALLS_PER_STEP = 3;
    static final int MAX_ANSWER_LENGTH = 2000;
    /**
     * Tempo total da conversa. E' conferido entre os passos: uma chamada ja em
     * andamento nao e' interrompida, mas e' limitada pelo timeout de leitura
     * do cliente.
     */
    static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(45);

    static final String NO_DATA_ANSWER =
            "Você ainda não tem tarefas em aberto, hábitos ativos nem metas em andamento. "
                    + "Cadastre alguns itens para eu poder ajudar.";

    // Caracteres de controle e invisiveis, menos a quebra de linha, que a resposta pode ter.
    private static final Pattern UNSAFE_CHARACTERS =
            Pattern.compile("[\\p{Cntrl}\\p{Cf}&&[^\\n]]", Pattern.UNICODE_CHARACTER_CLASS);

    private final AiAvailability availability;
    private final AiQuotaService quotaService;
    private final ContextBuilder contextBuilder;
    private final CoachTools tools;
    private final AiRequestLogService logService;
    private final AiProperties properties;
    private final AiMetrics metrics;
    private final int maxSteps;
    private final Duration totalTimeout;
    private final LongSupplier nanoTime;

    @Autowired
    public CoachService(AiAvailability availability, AiQuotaService quotaService, ContextBuilder contextBuilder,
                        CoachTools tools, AiRequestLogService logService, AiProperties properties,
                        AiMetrics metrics) {
        this(availability, quotaService, contextBuilder, tools, logService, properties, metrics,
                MAX_STEPS, TOTAL_TIMEOUT, System::nanoTime);
    }

    /** Para testes: limites e relogio controlados. */
    CoachService(AiAvailability availability, AiQuotaService quotaService, ContextBuilder contextBuilder,
                 CoachTools tools, AiRequestLogService logService, AiProperties properties, AiMetrics metrics,
                 int maxSteps, Duration totalTimeout, LongSupplier nanoTime) {
        this.availability = availability;
        this.quotaService = quotaService;
        this.contextBuilder = contextBuilder;
        this.tools = tools;
        this.logService = logService;
        this.properties = properties;
        this.metrics = metrics;
        this.maxSteps = maxSteps;
        this.totalTimeout = totalTimeout;
        this.nanoTime = nanoTime;
    }

    public CoachResponseDTO ask(UUID userId, String question) {
        LlmClient client = requireAccess(userId);

        // Uma leitura so, ja escopada pelo userId do token. As ferramentas filtram este contexto.
        BuiltContext built = contextBuilder.build(userId);
        if (built.context().isEmpty()) {
            return new CoachResponseDTO(NO_DATA_ANSWER, List.of(), 0); // nada a consultar: sem chamada nem cota
        }

        long start = nanoTime.getAsLong();
        List<ToolExchange> history = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();

        for (int step = 1; ; step++) {
            if (nanoTime.getAsLong() - start > totalTimeout.toNanos()) {
                log.warn("Coach agent stopped: {}", Type.TIMEOUT);
                throw new CoachAgentException(Type.TIMEOUT);
            }

            LlmToolResponse response;
            long callStart = nanoTime.getAsLong();
            try {
                response = client.chat(new LlmToolRequest(CoachPrompt.SYSTEM_MESSAGE, question, tools.specs(),
                        List.copyOf(history)));
            } catch (LlmProviderException e) {
                AiRequestStatus status = e.getType() == LlmProviderException.Type.TIMEOUT
                        ? AiRequestStatus.TIMEOUT : AiRequestStatus.PROVIDER_ERROR;
                record(userId, properties.model(), 0, 0, (nanoTime.getAsLong() - callStart) / 1_000_000, status);
                throw e;
            }

            try {
                if (!response.hasToolCalls()) {
                    String answer = validateAnswer(response, built);
                    record(userId, response, AiRequestStatus.SUCCESS);
                    return new CoachResponseDTO(answer, List.copyOf(toolsUsed), step);
                }
                if (step >= maxSteps) {
                    throw new CoachAgentException(Type.STEP_LIMIT);
                }
                if (response.toolCalls().size() > MAX_TOOL_CALLS_PER_STEP) {
                    throw new CoachAgentException(Type.TOO_MANY_TOOL_CALLS);
                }
                List<ToolResult> results = new ArrayList<>();
                for (ToolCall call : response.toolCalls()) {
                    Map<String, Object> result = tools.execute(call.name(), call.args(), built.context());
                    results.add(new ToolResult(call.id(), call.name(), result));
                    toolsUsed.add(call.name());
                }
                history.add(new ToolExchange(response.rawModelTurn(), results));
            } catch (CoachAgentException e) {
                // So o tipo vai para o log: nome de ferramenta e argumentos sao texto do modelo.
                record(userId, response, AiRequestStatus.INVALID_OUTPUT);
                log.warn("Coach agent stopped: {}", e.getType());
                throw e;
            }
            record(userId, response, AiRequestStatus.TOOL_STEP);
        }
    }

    // --- porteiro: modulo ligado, usuario permitido, cota ---

    private LlmClient requireAccess(UUID userId) {
        LlmClient client;
        try {
            client = availability.requireClient();
        } catch (AiDisabledException e) {
            metrics.recordRejection(AiFeature.COACH, Rejection.DISABLED);
            throw e;
        }
        try {
            availability.requireAllowed(userId);
        } catch (AiDisabledException e) {
            metrics.recordRejection(AiFeature.COACH, Rejection.NOT_ALLOWED);
            throw e;
        }
        try {
            quotaService.assertWithinQuota(userId, AiFeature.COACH);
        } catch (AiQuotaExceededException e) {
            metrics.recordRejection(AiFeature.COACH, Rejection.QUOTA_EXCEEDED);
            throw e;
        }
        return client;
    }

    // --- resposta final: saida nao confiavel ---

    private static String validateAnswer(LlmToolResponse response, BuiltContext built) {
        if (response.truncated() || response.text() == null) {
            throw new CoachAgentException(Type.INVALID_ANSWER);
        }
        String answer = UNSAFE_CHARACTERS.matcher(response.text()).replaceAll("").trim();
        if (answer.isEmpty() || answer.length() > MAX_ANSWER_LENGTH) {
            throw new CoachAgentException(Type.INVALID_ANSWER);
        }
        return IndexText.humanize(answer, IndexText.titlesByIndex(built.context()));
    }

    // --- log e metricas: so metadados ---

    private void record(UUID userId, LlmToolResponse response, AiRequestStatus status) {
        record(userId, response.modelName(), response.inputTokens(), response.outputTokens(),
                response.latencyMs(), status);
    }

    private void record(UUID userId, String model, int inputTokens, int outputTokens, long latencyMs,
                        AiRequestStatus status) {
        metrics.recordCall(AiFeature.COACH, properties.provider(), status, latencyMs, inputTokens, outputTokens);
        logService.record(userId, AiFeature.COACH, CoachPrompt.PROMPT_VERSION, properties.provider(), model,
                inputTokens, outputTokens, latencyMs, status);
        log.info("AI call finished: feature={}, status={}, latencyMs={}", AiFeature.COACH, status, latencyMs);
    }
}
