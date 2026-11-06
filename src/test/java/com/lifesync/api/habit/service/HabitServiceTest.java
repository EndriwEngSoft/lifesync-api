package com.lifesync.api.habit.service;

import com.lifesync.api.exception.DuplicateResourceException;
import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.dto.HabitRequestDTO;
import com.lifesync.api.habit.dto.HabitResponseDTO;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.entity.HabitHistory;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.habit.repository.HabitHistoryRepository;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre a condicao de corrida do check-in: mesmo a checagem "existsBy..."
 * passando, uma violacao da constraint unica do banco no saveAndFlush
 * ainda deve virar DuplicateResourceException, nao vazar como
 * DataIntegrityViolationException crua. Cobre tambem o reset de
 * currentStreak quando updateHabit muda a frequencia do habito.
 */
@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitHistoryRepository habitHistoryRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserService userService;

    // Terca 2026-11-03 02:30 UTC = segunda 2026-11-02 23:30 em Sao Paulo (UTC-3).
    // Data escolhida para provar que "hoje" vem do Clock E do fuso do usuario.
    private static final Instant NOW = Instant.parse("2026-11-03T02:30:00Z");

    private HabitService habitService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        habitService = new HabitService(habitRepository, habitHistoryRepository, goalRepository, userService, fixedClock);
    }

    @Test
    void checkIn_WhenInsertHitsUniqueConstraint_ThrowsDuplicateResourceException() {
        UUID userId = UUID.randomUUID();
        UUID habitId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .name("Nome")
                .email("user@lifesync.com")
                .passwordHash("hash")
                .timezone("America/Sao_Paulo")
                .active(true)
                .build();

        Habit habit = Habit.builder()
                .id(habitId)
                .name("Ler")
                .frequency(HabitFrequency.DAILY)
                .targetPerPeriod(1)
                .active(true)
                .user(user)
                .build();

        when(habitRepository.findByIdAndUserId(habitId, userId)).thenReturn(Optional.of(habit));
        when(habitHistoryRepository.existsByHabitIdAndCheckInDate(eq(habitId), any(LocalDate.class))).thenReturn(false);
        when(habitHistoryRepository.findTopByHabitIdAndCheckInDateBeforeOrderByCheckInDateDesc(eq(habitId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(habitHistoryRepository)
                .saveAndFlush(any(HabitHistory.class));

        assertThrows(DuplicateResourceException.class, () -> habitService.checkIn(habitId, userId));
    }

    @Test
    void checkIn_UsesClockAndUserTimezoneToDecideToday() {
        UUID userId = UUID.randomUUID();
        UUID habitId = UUID.randomUUID();

        User user = User.builder().id(userId).timezone("America/Sao_Paulo").build();
        Habit habit = Habit.builder()
                .id(habitId)
                .name("Ler")
                .frequency(HabitFrequency.DAILY)
                .active(true)
                .user(user)
                .build();

        when(habitRepository.findByIdAndUserId(habitId, userId)).thenReturn(Optional.of(habit));
        when(habitHistoryRepository.existsByHabitIdAndCheckInDate(any(), any())).thenReturn(false);
        when(habitHistoryRepository.findTopByHabitIdAndCheckInDateBeforeOrderByCheckInDateDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.checkIn(habitId, userId);

        ArgumentCaptor<HabitHistory> saved = ArgumentCaptor.forClass(HabitHistory.class);
        verify(habitHistoryRepository).saveAndFlush(saved.capture());
        assertEquals(LocalDate.of(2026, 11, 2), saved.getValue().getCheckInDate(),
                "02:30 UTC de terca ainda e segunda a noite em Sao Paulo");
    }

    // "Hoje" = quinta 2026-11-05. Semana atual: seg 11-02 a dom 11-08.
    @ParameterizedTest(name = "{0}, streak {1}, ultimo check-in {2} -> {3}")
    @CsvSource({
            // diario
            "DAILY,   3, 2026-11-04, 4",   // ontem: consecutivo
            "DAILY,   3, 2026-11-02, 1",   // lacuna
            // semanal
            "WEEKLY,  2, 2026-10-30, 3",   // sexta da semana passada: consecutivo
            "WEEKLY,  5, 2026-11-03, 5",   // mesma semana: mantem
            "WEEKLY,  0, 2026-11-03, 1",   // mesma semana apos trocar a frequencia: piso 1
            "WEEKLY,  4, 2026-10-20, 1",   // pulou semanas: lacuna
            // mensal
            "MONTHLY, 2, 2026-10-20, 3",   // mes passado: consecutivo
            "MONTHLY, 4, 2026-11-01, 4",   // mesmo mes: mantem
            "MONTHLY, 0, 2026-11-01, 1",   // mesmo mes apos trocar a frequencia: piso 1
            "MONTHLY, 4, 2026-09-15, 1"    // pulou meses: lacuna
    })
    void checkIn_CalculatesStreakByFrequencyAndPreviousCheckIn(
            HabitFrequency frequency, int streak, LocalDate previousCheckIn, int expectedStreak) {
        Habit habit = habitWithStreak(frequency, streak);
        stubCheckIn(habit, Optional.of(historyOn(previousCheckIn)));

        HabitResponseDTO response = checkInOn(LocalDate.of(2026, 11, 5), habit);

        assertEquals(expectedStreak, response.getCurrentStreak());
    }

    @Test
    void checkIn_FirstCheckIn_StartsStreakAtOne() {
        Habit habit = habitWithStreak(HabitFrequency.DAILY, 0);
        stubCheckIn(habit, Optional.empty());

        HabitResponseDTO response = checkInOn(LocalDate.of(2026, 11, 5), habit);

        assertEquals(1, response.getCurrentStreak());
        assertEquals(1, response.getLongestStreak());
    }

    @Test
    void checkIn_WhenStreakGrows_UpdatesLongestStreak() {
        Habit habit = habitWithStreak(HabitFrequency.DAILY, 3);
        stubCheckIn(habit, Optional.of(historyOn(LocalDate.of(2026, 11, 4))));

        HabitResponseDTO response = checkInOn(LocalDate.of(2026, 11, 5), habit);

        assertEquals(4, response.getCurrentStreak());
        assertEquals(4, response.getLongestStreak());
    }

    private Habit habitWithStreak(HabitFrequency frequency, int streak) {
        User user = User.builder().id(UUID.randomUUID()).timezone("UTC").build();
        return Habit.builder()
                .id(UUID.randomUUID())
                .name("Ler")
                .frequency(frequency)
                .currentStreak(streak)
                .longestStreak(streak)
                .active(true)
                .user(user)
                .build();
    }

    private HabitHistory historyOn(LocalDate date) {
        HabitHistory history = new HabitHistory();
        history.setCheckInDate(date);
        return history;
    }

    private void stubCheckIn(Habit habit, Optional<HabitHistory> previous) {
        when(habitRepository.findByIdAndUserId(habit.getId(), habit.getUser().getId())).thenReturn(Optional.of(habit));
        when(habitHistoryRepository.existsByHabitIdAndCheckInDate(any(), any())).thenReturn(false);
        when(habitHistoryRepository.findTopByHabitIdAndCheckInDateBeforeOrderByCheckInDateDesc(any(), any()))
                .thenReturn(previous);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Faz o check-in com o relogio fixado ao meio-dia UTC de {@code today} (usuario em UTC). */
    private HabitResponseDTO checkInOn(LocalDate today, Habit habit) {
        Clock clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        HabitService service = new HabitService(
                habitRepository, habitHistoryRepository, goalRepository, userService, clock);
        return service.checkIn(habit.getId(), habit.getUser().getId());
    }

    @Test
    void updateHabit_WhenFrequencyChanges_ResetsCurrentStreak() {
        UUID userId = UUID.randomUUID();
        UUID habitId = UUID.randomUUID();

        Habit habit = Habit.builder()
                .id(habitId)
                .name("Ler")
                .frequency(HabitFrequency.DAILY)
                .targetPerPeriod(1)
                .currentStreak(10)
                .longestStreak(15)
                .active(true)
                .build();

        HabitRequestDTO request = new HabitRequestDTO();
        request.setName("Ler");
        request.setFrequency(HabitFrequency.WEEKLY);
        request.setTargetPerPeriod(1);

        when(habitRepository.findByIdAndUserId(habitId, userId)).thenReturn(Optional.of(habit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponseDTO response = habitService.updateHabit(habitId, request, userId);

        assertEquals(0, response.getCurrentStreak(), "currentStreak deveria zerar quando a frequencia muda");
        assertEquals(15, response.getLongestStreak(), "longestStreak nao deveria ser afetado pela troca de frequencia");
    }

    @Test
    void updateHabit_WhenFrequencyUnchanged_KeepsCurrentStreak() {
        UUID userId = UUID.randomUUID();
        UUID habitId = UUID.randomUUID();

        Habit habit = Habit.builder()
                .id(habitId)
                .name("Ler")
                .frequency(HabitFrequency.DAILY)
                .targetPerPeriod(1)
                .currentStreak(10)
                .longestStreak(15)
                .active(true)
                .build();

        HabitRequestDTO request = new HabitRequestDTO();
        request.setName("Ler - titulo atualizado");
        request.setFrequency(HabitFrequency.DAILY);
        request.setTargetPerPeriod(2);

        when(habitRepository.findByIdAndUserId(habitId, userId)).thenReturn(Optional.of(habit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponseDTO response = habitService.updateHabit(habitId, request, userId);

        assertEquals(10, response.getCurrentStreak(), "currentStreak deveria ser preservado quando a frequencia nao muda");
    }

    @Test
    void createHabit_WithValidGoalId_LinksHabitToGoal() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        User user = User.builder().id(userId).name("Nome").build();
        Goal fakeGoal = new Goal();
        fakeGoal.setId(goalId);

        HabitRequestDTO request = new HabitRequestDTO();
        request.setName("Meditar");
        request.setFrequency(HabitFrequency.DAILY);
        request.setTargetPerPeriod(1);
        request.setGoalId(goalId);

        when(userService.findById(userId)).thenReturn(Optional.of(user));
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(fakeGoal));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponseDTO result = habitService.createHabit(request, userId);

        assertEquals(goalId, result.getGoalId(), "goalId returned must match the linked Goal");
    }

    @Test
    void createHabit_WithGoalIdFromAnotherUser_ThrowsResourceNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID goalIdFromAnotherUser = UUID.randomUUID();

        User user = User.builder().id(userId).name("Nome").build();

        HabitRequestDTO request = new HabitRequestDTO();
        request.setName("Meditar");
        request.setFrequency(HabitFrequency.DAILY);
        request.setTargetPerPeriod(1);
        request.setGoalId(goalIdFromAnotherUser);

        when(userService.findById(userId)).thenReturn(Optional.of(user));
        when(goalRepository.findByIdAndUserId(goalIdFromAnotherUser, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                habitService.createHabit(request, userId));
    }

    @Test
    void createHabit_WithoutGoalId_LeavesGoalIdNull() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).name("Nome").build();

        HabitRequestDTO request = new HabitRequestDTO();
        request.setName("Meditar");
        request.setFrequency(HabitFrequency.DAILY);
        request.setTargetPerPeriod(1);

        when(userService.findById(userId)).thenReturn(Optional.of(user));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponseDTO result = habitService.createHabit(request, userId);

        assertEquals(null, result.getGoalId(), "goalId must stay null when the request doesn't provide one");
        verify(goalRepository, never()).findByIdAndUserId(any(), any());
    }
}
