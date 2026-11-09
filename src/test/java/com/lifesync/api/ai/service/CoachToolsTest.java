package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.ToolSpec;
import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.dto.UserContextDTO.GoalContext;
import com.lifesync.api.ai.dto.UserContextDTO.HabitContext;
import com.lifesync.api.ai.dto.UserContextDTO.TaskContext;
import com.lifesync.api.ai.exception.CoachAgentException;
import com.lifesync.api.ai.exception.CoachAgentException.Type;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoachToolsTest {

    private final CoachTools tools = new CoachTools();

    private static final UserContextDTO CONTEXT = new UserContextDTO("2026-11-09", "MONDAY", "America/Sao_Paulo",
            List.of(new TaskContext("T1", "Pagar a conta de luz", "HIGH", "PENDING", "2026-11-05", true),
                    new TaskContext("T2", "Renovar o seguro", "LOW", "PENDING", "2026-11-07", true),
                    new TaskContext("T3", "Comprar presente", "LOW", "PENDING", "2026-11-20", false)),
            List.of(new HabitContext("H1", "Meditar", "DAILY", 21, "2026-11-08", false, true),
                    new HabitContext("H2", "Correr", "WEEKLY", 4, "2026-11-09", true, false)),
            List.of(new GoalContext("G1", "Juntar dinheiro", "IN_PROGRESS", 25.0, "2026-12-31")));

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("itens");
    }

    private void assertRejected(String tool, Map<String, Object> args, Type expected) {
        assertThatThrownBy(() -> tools.execute(tool, args, CONTEXT))
                .isInstanceOfSatisfying(CoachAgentException.class, e -> assertThat(e.getType()).isEqualTo(expected));
    }

    // --- o que cada ferramenta devolve ---

    @Test
    void overdueTasks_ReturnsOnlyOverdueOnes() {
        Map<String, Object> result = tools.execute(CoachTools.OVERDUE_TASKS, Map.of(), CONTEXT);

        assertThat(items(result)).extracting(i -> i.get("indice")).containsExactly("T1", "T2");
        assertThat(result.get("total")).isEqualTo(2);
        assertThat(items(result).getFirst()).containsOnlyKeys("indice", "titulo", "prioridade", "prazo");
    }

    @Test
    void overdueTasks_RespectsTheLimit() {
        Map<String, Object> result = tools.execute(CoachTools.OVERDUE_TASKS, Map.of("limite", 1L), CONTEXT);

        assertThat(items(result)).extracting(i -> i.get("indice")).containsExactly("T1");
    }

    @Test
    void habitsAtRisk_ReturnsOnlyHabitsAtRisk() {
        Map<String, Object> result = tools.execute(CoachTools.HABITS_AT_RISK, Map.of(), CONTEXT);

        assertThat(items(result)).extracting(i -> i.get("nome")).containsExactly("Meditar");
    }

    @Test
    void goalsProgress_ReturnsGoalsWithProgress() {
        Map<String, Object> result = tools.execute(CoachTools.GOALS_PROGRESS, null, CONTEXT);

        assertThat(items(result)).singleElement().satisfies(goal -> {
            assertThat(goal.get("titulo")).isEqualTo("Juntar dinheiro");
            assertThat(goal.get("progressoPercentual")).isEqualTo(25.0);
        });
    }

    // --- nome e argumentos vem do modelo: entrada nao confiavel ---

    @Test
    void unknownTool_IsRejected() {
        assertRejected("apagar_todas_as_tarefas", Map.of(), Type.UNKNOWN_TOOL);
        assertRejected("", Map.of(), Type.UNKNOWN_TOOL);
        assertRejected(null, Map.of(), Type.UNKNOWN_TOOL);
        assertRejected("TAREFAS_ATRASADAS", Map.of(), Type.UNKNOWN_TOOL);
    }

    @Test
    void unknownArgument_IsRejected_EvenWhenItLooksLikeAUserId() {
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("userId", "5f0c1f6e-2b7a-4a55-8f43-7f6f0f3f9a11"),
                Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.HABITS_AT_RISK, Map.of("limite", 5L), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.GOALS_PROGRESS, Map.of("limite", 5L, "status", "COMPLETED"),
                Type.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void limitOutOfRangeOrOfTheWrongType_IsRejected() {
        Map<String, Object> nullLimit = new HashMap<>();
        nullLimit.put("limite", null);

        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", 0L), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", -1L), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", 21L), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", Long.MAX_VALUE), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", new java.math.BigInteger("99999999999999999999")),
                Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", "5"), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", 5.5), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", true), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, Map.of("limite", List.of(5L)), Type.INVALID_TOOL_ARGUMENTS);
        assertRejected(CoachTools.OVERDUE_TASKS, nullLimit, Type.INVALID_TOOL_ARGUMENTS);
    }

    // --- o que e' declarado ao modelo ---

    @Test
    void specs_DeclareOnlyTheThreeReadOnlyTools_WithoutAnyIdentifierParameter() {
        List<ToolSpec> specs = tools.specs();

        assertThat(specs).extracting(ToolSpec::name).containsExactly(
                CoachTools.OVERDUE_TASKS, CoachTools.HABITS_AT_RISK, CoachTools.GOALS_PROGRESS);
        for (ToolSpec spec : specs) {
            assertThat(spec.parametersSchema().get("additionalProperties")).isEqualTo(false);
            assertThat(spec.parametersSchema().toString().toLowerCase()).doesNotContain("userid", "user_id", "uuid");
        }
    }
}
