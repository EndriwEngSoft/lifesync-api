package com.lifesync.api.habit.service;

import com.lifesync.api.exception.DuplicateResourceException;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.entity.HabitHistory;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.habit.repository.HabitHistoryRepository;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre a condicao de corrida do check-in: mesmo a checagem "existsBy..."
 * passando, uma violacao da constraint unica do banco no saveAndFlush
 * ainda deve virar DuplicateResourceException, nao vazar como
 * DataIntegrityViolationException crua.
 */
@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitHistoryRepository habitHistoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private HabitService habitService;

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
}
