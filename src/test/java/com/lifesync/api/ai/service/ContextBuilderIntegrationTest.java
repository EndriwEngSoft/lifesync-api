package com.lifesync.api.ai.service;

import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.service.ContextBuilder.BuiltContext;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.entity.HabitHistory;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.habit.repository.HabitHistoryRepository;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContextBuilder contra um Postgres real: as consultas escopadas por usuario
 * e, principalmente, o contexto do usuario A sem nada do usuario B.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class ContextBuilderIntegrationTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-06");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private HabitRepository habitRepository;
    @Autowired
    private HabitHistoryRepository historyRepository;
    @Autowired
    private GoalRepository goalRepository;

    private ContextBuilder builder;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        builder = new ContextBuilder(taskRepository, habitRepository, historyRepository, goalRepository,
                userRepository, Clock.fixed(Instant.parse("2026-11-06T16:00:00Z"), ZoneOffset.UTC));
        userA = newUser("a");
        userB = newUser("b");
    }

    @Test
    void contextOfUserA_ContainsNothingFromUserB() {
        // Dados do A
        task(userA, "A-tarefa-aberta", Status.PENDING);
        Habit habitA = habit(userA, "A-habito", true);
        checkIn(habitA, TODAY.minusDays(1));
        goal(userA, "A-meta", GoalStatus.IN_PROGRESS);
        // Dados do B (mais numerosos, para qualquer vazamento aparecer)
        task(userB, "B-tarefa-aberta-1", Status.PENDING);
        task(userB, "B-tarefa-aberta-2", Status.IN_PROGRESS);
        Habit habitB = habit(userB, "B-habito", true);
        checkIn(habitB, TODAY);
        goal(userB, "B-meta", GoalStatus.IN_PROGRESS);

        BuiltContext built = builder.build(userA.getId());
        String json = JsonMapper.builder().build().writeValueAsString(built.context());

        assertThat(json).contains("A-tarefa-aberta", "A-habito", "A-meta");
        assertThat(json).doesNotContain("B-tarefa", "B-habito", "B-meta");
        assertThat(built.context().tasks()).hasSize(1);
        assertThat(built.context().habits()).hasSize(1);
        assertThat(built.context().goals()).hasSize(1);
        // Todos os ids mapeados pertencem ao A
        assertThat(built.references().values()).extracting(ContextBuilder.Reference::id)
                .doesNotContain(habitB.getId())
                .hasSize(3);
        // O ultimo check-in lido e' o do habito do A, nao o do B
        assertThat(built.context().habits().getFirst().lastCheckInDate()).isEqualTo("2026-11-05");
        assertThat(built.context().habits().getFirst().atRisk()).isTrue();
    }

    @Test
    void contextOfUserB_IsIndependent() {
        task(userA, "A-tarefa", Status.PENDING);
        task(userB, "B-tarefa", Status.PENDING);

        String json = JsonMapper.builder().build().writeValueAsString(builder.build(userB.getId()).context());

        assertThat(json).contains("B-tarefa").doesNotContain("A-tarefa");
    }

    @Test
    void onlyOpenTasksActiveHabitsAndInProgressGoalsAreRead() {
        task(userA, "aberta", Status.PENDING);
        task(userA, "em andamento", Status.IN_PROGRESS);
        task(userA, "concluida", Status.DONE);
        task(userA, "cancelada", Status.CANCELLED);
        habit(userA, "habito ativo", true);
        habit(userA, "habito removido", false);
        goal(userA, "meta em andamento", GoalStatus.IN_PROGRESS);
        goal(userA, "meta concluida", GoalStatus.COMPLETED);
        goal(userA, "meta abandonada", GoalStatus.ABANDONED);

        UserContextDTO context = builder.build(userA.getId()).context();

        assertThat(context.tasks()).extracting(UserContextDTO.TaskContext::title)
                .containsExactlyInAnyOrder("aberta", "em andamento");
        assertThat(context.habits()).extracting(UserContextDTO.HabitContext::name).containsExactly("habito ativo");
        assertThat(context.goals()).extracting(UserContextDTO.GoalContext::title)
                .containsExactly("meta em andamento");
    }

    @Test
    void lastCheckIn_IsTheMostRecentDatePerHabit() {
        Habit h1 = habit(userA, "h1", true);
        Habit h2 = habit(userA, "h2", true);
        checkIn(h1, TODAY.minusDays(10));
        checkIn(h1, TODAY.minusDays(2));
        checkIn(h1, TODAY.minusDays(6));
        checkIn(h2, TODAY.minusDays(1));

        var rows = historyRepository.findLastCheckIns(userA.getId(), java.util.List.of(h1.getId(), h2.getId()));

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> r.getHabitId().equals(h1.getId()))
                .singleElement().satisfies(r -> assertThat(r.getLastCheckInDate()).isEqualTo(TODAY.minusDays(2)));
        assertThat(rows).filteredOn(r -> r.getHabitId().equals(h2.getId()))
                .singleElement().satisfies(r -> assertThat(r.getLastCheckInDate()).isEqualTo(TODAY.minusDays(1)));
    }

    @Test
    void lastCheckInQuery_DoesNotReturnHabitsOfAnotherUserEvenIfTheIdIsGiven() {
        Habit habitB = habit(userB, "B-habito", true);
        checkIn(habitB, TODAY);

        var rows = historyRepository.findLastCheckIns(userA.getId(), java.util.List.of(habitB.getId()));

        assertThat(rows).isEmpty();
    }

    @Test
    void userWithoutData_GetsAnEmptyContext() {
        assertThat(builder.build(userA.getId()).context().isEmpty()).isTrue();
    }

    // --- apoio ---

    private User newUser(String tag) {
        String unique = tag + "-" + UUID.randomUUID();
        return userRepository.saveAndFlush(User.builder().name("Nome " + tag).username(unique)
                .email(unique + "@teste.com").passwordHash("hash").timezone("America/Sao_Paulo").build());
    }

    private Task task(User owner, String title, Status status) {
        return taskRepository.saveAndFlush(Task.builder().title(title).priority(Priority.MEDIUM).status(status)
                .user(owner).build());
    }

    private Habit habit(User owner, String name, boolean active) {
        return habitRepository.saveAndFlush(Habit.builder().name(name).frequency(HabitFrequency.DAILY)
                .targetPerPeriod(1).currentStreak(3).active(active).user(owner).build());
    }

    private void checkIn(Habit habit, LocalDate date) {
        historyRepository.saveAndFlush(HabitHistory.builder().habit(habit).checkInDate(date).completed(true).build());
    }

    private Goal goal(User owner, String title, GoalStatus status) {
        return goalRepository.saveAndFlush(Goal.builder().title(title).currentValue(new BigDecimal("10"))
                .targetValue(new BigDecimal("100")).unit("un").status(status).user(owner).build());
    }
}
