package com.lifesync.api.ai.service;

import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.dto.UserContextDTO.GoalContext;
import com.lifesync.api.ai.dto.UserContextDTO.HabitContext;
import com.lifesync.api.ai.dto.UserContextDTO.TaskContext;
import com.lifesync.api.ai.enums.ReferenceType;
import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.habit.repository.HabitHistoryRepository;
import com.lifesync.api.habit.repository.HabitHistoryRepository.LastCheckIn;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Monta o contexto que vai ao LLM a partir dos dados do usuario do token.
 *
 * Garantias:
 * - so le dados via repositorios escopados por userId (nunca busca por id solto);
 * - nao envia nome, e-mail, username, senha, UUIDs nem descricoes livres: so
 *   titulos/nomes, e mesmo estes saneados (sem quebra de linha, e-mails e
 *   numeros longos mascarados, tamanho limitado);
 * - limita a quantidade de itens e identifica cada um por indice curto
 *   (T1, H1, G1); o mapa indice -> id real fica so no servidor;
 * - "hoje" e as regras de sequencia usam o Clock injetado e o fuso do usuario.
 *
 * O mascaramento de dados pessoais nos titulos e' de melhor esforco, nao uma
 * garantia: titulos livres podem conter dados de terceiros (ver modelo de
 * ameacas).
 */
@Service
public class ContextBuilder {

    static final int MAX_TASKS = 20;
    static final int MAX_HABITS = 10;
    static final int MAX_GOALS = 10;
    static final int MAX_TITLE_LENGTH = 80;
    /** Teto de leitura do banco antes de ranquear as tarefas. */
    private static final int TASK_FETCH_CAP = 200;

    // UNICODE_CHARACTER_CLASS: sem ela, \s e \p{Cntrl} so cobrem ASCII e deixariam passar as quebras
    // de linha Unicode (U+2028, U+2029, U+0085). \p{Cf} cobre os invisiveis (largura zero, direcao).
    private static final Pattern CONTROL_OR_SPACES =
            Pattern.compile("[\\p{Cntrl}\\p{Cf}\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{9,}");

    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final HabitHistoryRepository habitHistoryRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ContextBuilder(TaskRepository taskRepository, HabitRepository habitRepository,
                          HabitHistoryRepository habitHistoryRepository, GoalRepository goalRepository,
                          UserRepository userRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.habitRepository = habitRepository;
        this.habitHistoryRepository = habitHistoryRepository;
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Referencia a um item real, guardada so no servidor. */
    public record Reference(ReferenceType type, UUID id) {}

    /** Contexto para o LLM + mapa de indice curto para o item real. */
    public record BuiltContext(UserContextDTO context, Map<String, Reference> references) {}

    @Transactional(readOnly = true)
    public BuiltContext build(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ZoneId zone = UserZones.of(user);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        Map<String, Reference> references = new LinkedHashMap<>();
        List<TaskContext> tasks = buildTasks(userId, today, references);
        List<HabitContext> habits = buildHabits(userId, today, references);
        List<GoalContext> goals = buildGoals(userId, references);

        UserContextDTO context = new UserContextDTO(today.toString(), today.getDayOfWeek().name(), zone.getId(),
                tasks, habits, goals);
        return new BuiltContext(context, references);
    }

    // --- tarefas ---

    private List<TaskContext> buildTasks(UUID userId, LocalDate today, Map<String, Reference> references) {
        List<Task> open = taskRepository.findByUserIdAndStatusIn(userId,
                List.of(Status.PENDING, Status.IN_PROGRESS), PageRequest.of(0, TASK_FETCH_CAP));

        Comparator<Task> ranking = Comparator
                .comparing((Task t) -> !isOverdue(t, today))
                .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((Task t) -> priorityRank(t.getPriority()), Comparator.reverseOrder());

        List<Task> top = open.stream().sorted(ranking).limit(MAX_TASKS).toList();

        List<TaskContext> result = new ArrayList<>();
        for (Task task : top) {
            String index = ReferenceType.TASK.indexFor(result.size() + 1);
            references.put(index, new Reference(ReferenceType.TASK, task.getId()));
            result.add(new TaskContext(index, sanitize(task.getTitle()), name(task.getPriority()),
                    name(task.getStatus()), task.getDueDate() == null ? null : task.getDueDate().toString(),
                    isOverdue(task, today)));
        }
        return result;
    }

    private static boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null && task.getDueDate().isBefore(today);
    }

    private static int priorityRank(Priority priority) {
        return priority == null ? -1 : priority.ordinal();
    }

    // --- habitos ---

    private List<HabitContext> buildHabits(UUID userId, LocalDate today, Map<String, Reference> references) {
        List<Habit> habits = habitRepository.findByUserIdAndActiveTrue(userId,
                PageRequest.of(0, MAX_HABITS, Sort.by("createdAt").ascending()));
        if (habits.isEmpty()) {
            return List.of();
        }

        Map<UUID, LocalDate> lastCheckIns = habitHistoryRepository
                .findLastCheckIns(userId, habits.stream().map(Habit::getId).toList())
                .stream()
                .collect(Collectors.toMap(LastCheckIn::getHabitId, LastCheckIn::getLastCheckInDate));

        List<HabitContext> result = new ArrayList<>();
        for (Habit habit : habits) {
            String index = ReferenceType.HABIT.indexFor(result.size() + 1);
            references.put(index, new Reference(ReferenceType.HABIT, habit.getId()));

            LocalDate last = lastCheckIns.get(habit.getId());
            StreakState state = streakState(habit.getFrequency(), habit.getCurrentStreak(), last, today);
            result.add(new HabitContext(index, sanitize(habit.getName()), name(habit.getFrequency()),
                    state.effectiveStreak(), last == null ? null : last.toString(),
                    last != null && last.equals(today), state.atRisk()));
        }
        return result;
    }

    /** Resultado do calculo de sequencia efetiva e risco. */
    record StreakState(int effectiveStreak, boolean atRisk) {}

    /**
     * Interpreta a sequencia guardada com as mesmas regras do modulo de habitos
     * (periodo = dia, semana iniciada na segunda ou mes), sem alterar esse
     * modulo. A sequencia guardada so e atualizada no check-in, entao pode estar
     * velha: se o ultimo check-in nao e do periodo atual nem do imediatamente
     * anterior, ela ja foi perdida e vale 0. Esta "em risco" quando ainda vale,
     * mas falta o check-in do periodo atual.
     */
    static StreakState streakState(HabitFrequency frequency, int storedStreak, LocalDate lastCheckIn,
                                   LocalDate today) {
        if (lastCheckIn == null || frequency == null) {
            return new StreakState(0, false);
        }
        LocalDate currentPeriod = periodStart(frequency, today);
        LocalDate lastPeriod = periodStart(frequency, lastCheckIn);

        boolean checkedThisPeriod = !lastPeriod.isBefore(currentPeriod);
        boolean checkedPreviousPeriod = nextPeriod(frequency, lastPeriod).equals(currentPeriod);
        boolean alive = checkedThisPeriod || checkedPreviousPeriod;

        int effective = alive ? Math.max(storedStreak, 0) : 0;
        boolean atRisk = alive && !checkedThisPeriod && effective > 0;
        return new StreakState(effective, atRisk);
    }

    private static LocalDate periodStart(HabitFrequency frequency, LocalDate date) {
        return switch (frequency) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate nextPeriod(HabitFrequency frequency, LocalDate periodStart) {
        return switch (frequency) {
            case DAILY -> periodStart.plusDays(1);
            case WEEKLY -> periodStart.plusWeeks(1);
            case MONTHLY -> periodStart.plusMonths(1);
        };
    }

    // --- metas ---

    private List<GoalContext> buildGoals(UUID userId, Map<String, Reference> references) {
        List<Goal> goals = goalRepository.findByUserIdWithFilters(userId, GoalStatus.IN_PROGRESS,
                PageRequest.of(0, MAX_GOALS, Sort.by("targetDate").ascending())).getContent();

        List<GoalContext> result = new ArrayList<>();
        for (Goal goal : goals) {
            String index = ReferenceType.GOAL.indexFor(result.size() + 1);
            references.put(index, new Reference(ReferenceType.GOAL, goal.getId()));
            result.add(new GoalContext(index, sanitize(goal.getTitle()), name(goal.getStatus()),
                    progress(goal), goal.getTargetDate() == null ? null : goal.getTargetDate().toString()));
        }
        return result;
    }

    private static double progress(Goal goal) {
        BigDecimal target = goal.getTargetValue();
        BigDecimal current = goal.getCurrentValue();
        if (target == null || current == null || target.signum() <= 0) {
            return 0;
        }
        BigDecimal pct = current.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
        return pct.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).doubleValue();
    }

    // --- saneamento de texto livre ---

    /**
     * Texto livre vira uma linha curta e sem dados obvios de contato: remove
     * caracteres de controle e quebras de linha (que ajudariam a forjar
     * instrucoes), mascara e-mails, CPFs e numeros longos, e limita o tamanho.
     */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String clean = CONTROL_OR_SPACES.matcher(text).replaceAll(" ").trim();
        clean = EMAIL.matcher(clean).replaceAll("[email]");
        clean = CPF.matcher(clean).replaceAll("[numero]");
        clean = LONG_NUMBER.matcher(clean).replaceAll("[numero]");
        if (clean.codePointCount(0, clean.length()) > MAX_TITLE_LENGTH) {
            int end = clean.offsetByCodePoints(0, MAX_TITLE_LENGTH);
            clean = clean.substring(0, end).trim() + "...";
        }
        return clean;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

}
