package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.ToolSpec;
import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.exception.CoachAgentException;
import com.lifesync.api.ai.exception.CoachAgentException.Type;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ferramentas SOMENTE LEITURA oferecidas ao modelo no assistente.
 *
 * O escopo por usuario nao depende do modelo: nenhuma ferramenta recebe
 * userId nem id de item. Elas so filtram o contexto que o servidor ja montou
 * para o usuario do token (ContextBuilder), entao o pior que um pedido forjado
 * consegue e' ler de novo dados do proprio usuario, ja minimizados e saneados.
 *
 * Nome e argumentos vem do modelo e sao entrada nao confiavel: ferramenta
 * desconhecida, argumento desconhecido, tipo errado ou valor fora do intervalo
 * encerram a conversa com erro tipado, sem executar nada.
 */
@Component
public class CoachTools {

    public static final String OVERDUE_TASKS = "tarefas_atrasadas";
    public static final String HABITS_AT_RISK = "habitos_em_risco";
    public static final String GOALS_PROGRESS = "progresso_das_metas";

    static final String LIMIT = "limite";
    static final int MAX_LIMIT = 20;

    private static final Map<String, Set<String>> ALLOWED_ARGUMENTS = Map.of(
            OVERDUE_TASKS, Set.of(LIMIT),
            HABITS_AT_RISK, Set.of(),
            GOALS_PROGRESS, Set.of(LIMIT));

    public List<ToolSpec> specs() {
        return List.of(
                new ToolSpec(OVERDUE_TASKS,
                        "Lista as tarefas em aberto do usuario que ja passaram do prazo, das mais atrasadas "
                                + "para as menos atrasadas.",
                        schemaWithLimit()),
                new ToolSpec(HABITS_AT_RISK,
                        "Lista os habitos ativos do usuario cuja sequencia sera perdida se nao houver "
                                + "check-in no periodo atual.",
                        emptySchema()),
                new ToolSpec(GOALS_PROGRESS,
                        "Lista as metas em andamento do usuario com o percentual de progresso e o prazo.",
                        schemaWithLimit()));
    }

    /**
     * Valida o pedido e executa a ferramenta sobre o contexto do usuario.
     *
     * @throws CoachAgentException UNKNOWN_TOOL ou INVALID_TOOL_ARGUMENTS
     */
    public Map<String, Object> execute(String name, Map<String, Object> args, UserContextDTO context) {
        Set<String> allowed = name == null ? null : ALLOWED_ARGUMENTS.get(name);
        if (allowed == null) {
            throw new CoachAgentException(Type.UNKNOWN_TOOL);
        }
        Map<String, Object> arguments = args == null ? Map.of() : args;
        if (!allowed.containsAll(arguments.keySet())) {
            throw new CoachAgentException(Type.INVALID_TOOL_ARGUMENTS);
        }
        int limit = limitOf(arguments);

        return switch (name) {
            case OVERDUE_TASKS -> overdueTasks(context, limit);
            case HABITS_AT_RISK -> habitsAtRisk(context);
            default -> goalsProgress(context, limit);
        };
    }

    // --- validacao ---

    private static int limitOf(Map<String, Object> arguments) {
        Object raw = arguments.get(LIMIT);
        if (raw == null) {
            if (arguments.containsKey(LIMIT)) {
                throw new CoachAgentException(Type.INVALID_TOOL_ARGUMENTS);
            }
            return MAX_LIMIT;
        }
        // So inteiros de verdade: "5", 5.5, true ou uma lista nao sao aceitos nem convertidos.
        if (!(raw instanceof Integer || raw instanceof Long)) {
            throw new CoachAgentException(Type.INVALID_TOOL_ARGUMENTS);
        }
        long value = ((Number) raw).longValue();
        if (value < 1 || value > MAX_LIMIT) {
            throw new CoachAgentException(Type.INVALID_TOOL_ARGUMENTS);
        }
        return (int) value;
    }

    // --- ferramentas ---

    private static Map<String, Object> overdueTasks(UserContextDTO context, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        // O contexto ja vem ordenado: atrasadas primeiro, da mais antiga para a mais recente.
        context.tasks().stream().filter(UserContextDTO.TaskContext::overdue).limit(limit).forEach(t -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("indice", t.shortIndex());
            item.put("titulo", t.title());
            item.put("prioridade", t.priority());
            item.put("prazo", t.dueDate());
            items.add(item);
        });
        return result(context, items);
    }

    private static Map<String, Object> habitsAtRisk(UserContextDTO context) {
        List<Map<String, Object>> items = new ArrayList<>();
        context.habits().stream().filter(UserContextDTO.HabitContext::atRisk).forEach(h -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("indice", h.shortIndex());
            item.put("nome", h.name());
            item.put("frequencia", h.frequency());
            item.put("sequenciaAtual", h.currentStreak());
            item.put("ultimoCheckIn", h.lastCheckInDate());
            items.add(item);
        });
        return result(context, items);
    }

    private static Map<String, Object> goalsProgress(UserContextDTO context, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        context.goals().stream().limit(limit).forEach(g -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("indice", g.shortIndex());
            item.put("titulo", g.title());
            item.put("progressoPercentual", g.progressPercentage());
            item.put("prazo", g.targetDate());
            items.add(item);
        });
        return result(context, items);
    }

    private static Map<String, Object> result(UserContextDTO context, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hoje", context.today());
        result.put("total", items.size());
        result.put("itens", items);
        return result;
    }

    // --- esquemas dos argumentos (JSON Schema) ---

    private static Map<String, Object> schemaWithLimit() {
        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("minimum", 1);
        limit.put("maximum", MAX_LIMIT);
        limit.put("description", "Quantidade maxima de itens a devolver.");

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(LIMIT, limit));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> emptySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("additionalProperties", false);
        return schema;
    }
}
