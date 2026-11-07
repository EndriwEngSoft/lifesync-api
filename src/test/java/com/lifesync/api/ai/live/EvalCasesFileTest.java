package com.lifesync.api.ai.live;

import com.lifesync.api.ai.live.WeeklyPlanLiveEvalTest.EvalCase;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o arquivo eval-cases.json SEM chamar provedor nenhum, para que um erro
 * de digitacao (enum, fuso, id repetido) apareca na suite padrao e nao so no
 * meio de uma avaliacao manual que gasta tokens.
 */
class EvalCasesFileTest {

    @Test
    void file_HasTenCasesWithUniqueIds() throws IOException {
        List<EvalCase> cases = WeeklyPlanLiveEvalTest.loadCases();

        assertThat(cases).hasSize(10);
        assertThat(cases).extracting(EvalCase::id).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(cases).allSatisfy(c -> assertThat(c.description()).isNotBlank());
    }

    @Test
    void everyCase_UsesOnlyValidTimezonesAndEnums() throws IOException {
        for (EvalCase c : WeeklyPlanLiveEvalTest.loadCases()) {
            assertThat(ZoneId.getAvailableZoneIds()).contains(c.timezone());
            if (c.tasks() != null) {
                c.tasks().forEach(t -> {
                    assertThat(t.title()).isNotBlank().hasSizeLessThanOrEqualTo(150);
                    Priority.valueOf(t.priority());
                    Status.valueOf(t.status());
                });
            }
            if (c.habits() != null) {
                c.habits().forEach(h -> {
                    assertThat(h.name()).isNotBlank().hasSizeLessThanOrEqualTo(150);
                    HabitFrequency.valueOf(h.frequency());
                });
            }
            if (c.goals() != null) {
                c.goals().forEach(g -> assertThat(g.title()).isNotBlank().hasSizeLessThanOrEqualTo(150));
            }
        }
    }

    @Test
    void file_CoversTheVariedScenarios() throws IOException {
        List<EvalCase> cases = WeeklyPlanLiveEvalTest.loadCases();

        assertThat(cases).extracting(EvalCase::id).contains("sem-dados", "so-tarefas-atrasadas",
                "habito-em-risco", "meta-atrasada", "titulos-longos", "prompt-injection", "muitos-itens",
                "fuso-diferente");
        assertThat(cases).filteredOn(c -> c.id().equals("sem-dados")).singleElement()
                .satisfies(c -> assertThat(c.expect().emptyPlan).isTrue());
        assertThat(cases).extracting(EvalCase::timezone).containsAnyOf("Asia/Tokyo");
        assertThat(cases).filteredOn(c -> c.id().equals("muitos-itens")).singleElement()
                .satisfies(c -> assertThat(c.bulk().tasks()).isGreaterThan(20));
    }
}
