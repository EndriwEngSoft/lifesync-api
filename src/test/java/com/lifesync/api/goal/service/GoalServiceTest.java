package com.lifesync.api.goal.service;

import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.goal.dto.GoalProgressRequestDTO;
import com.lifesync.api.goal.dto.GoalRequestDTO;
import com.lifesync.api.goal.dto.GoalResponseDTO;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.goal.repository.GoalProgressRepository;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste unitario com mocks (Mockito) - mesmo padrao de TaskServiceTest.
 * Foco nos quatro pontos de maior risco do modulo: currentValue/status
 * nascendo zerados na criacao, a transicao automatica pra COMPLETED
 * quando o progresso bate o alvo (e a ausencia dela quando nao bate), a
 * desvinculacao de Task/Habit no delete (em vez de cascade), e a defesa
 * contra IDOR.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalProgressRepository goalProgressRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private GoalService goalService;

    private UUID userId;
    private UUID goalId;
    private Goal fakeGoal;
    private User fakeUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        goalId = UUID.randomUUID();

        fakeUser = new User();
        fakeUser.setId(userId);
        fakeUser.setName("Usuário Teste");

        fakeGoal = new Goal();
        fakeGoal.setId(goalId);
        fakeGoal.setUser(fakeUser);
        fakeGoal.setTargetValue(BigDecimal.valueOf(1000));
        fakeGoal.setCurrentValue(BigDecimal.ZERO);
        fakeGoal.setStatus(GoalStatus.IN_PROGRESS);
        fakeGoal.setTasks(new ArrayList<>());
        fakeGoal.setHabits(new ArrayList<>());
    }

    @Test
    void createGoal_AlwaysStartsWithZeroCurrentValueAndInProgressStatus() {
        GoalRequestDTO dto = new GoalRequestDTO();
        dto.setTitle("Reserva de emergência");
        dto.setTargetValue(BigDecimal.valueOf(5000));
        dto.setUnit("R$");

        when(userService.findById(userId)).thenReturn(Optional.of(fakeUser));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoalResponseDTO result = goalService.createGoal(dto, userId);

        assertEquals(BigDecimal.ZERO, result.getCurrentValue(), "New goal must start at zero progress");
        assertEquals(GoalStatus.IN_PROGRESS, result.getStatus(), "New goal must start IN_PROGRESS");
    }

    @Test
    void recordProgress_ReachingTarget_CompletesGoal() {
        GoalProgressRequestDTO dto = new GoalProgressRequestDTO();
        dto.setValue(BigDecimal.valueOf(1000));

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(fakeGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoalResponseDTO result = goalService.recordProgress(goalId, userId, dto);

        assertEquals(GoalStatus.COMPLETED, result.getStatus(), "Reaching targetValue should auto-complete the goal");
        assertNotNull(result.getCompletedAt(), "completedAt should be set once the goal is completed");
    }

    @Test
    void recordProgress_BelowTarget_KeepsInProgress() {
        GoalProgressRequestDTO dto = new GoalProgressRequestDTO();
        dto.setValue(BigDecimal.valueOf(400));

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(fakeGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoalResponseDTO result = goalService.recordProgress(goalId, userId, dto);

        assertEquals(GoalStatus.IN_PROGRESS, result.getStatus(), "Progress below targetValue must not complete the goal");
        assertNull(result.getCompletedAt(), "completedAt must stay null while the goal is not complete");
    }

    @Test
    void deleteGoal_UnlinksTasksAndHabits_InsteadOfDeletingThem() {
        Task fakeTask = new Task();
        fakeTask.setId(UUID.randomUUID());
        fakeTask.setGoal(fakeGoal);

        Habit fakeHabit = new Habit();
        fakeHabit.setId(UUID.randomUUID());
        fakeHabit.setGoal(fakeGoal);

        fakeGoal.setTasks(List.of(fakeTask));
        fakeGoal.setHabits(List.of(fakeHabit));

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(fakeGoal));

        goalService.deleteGoal(goalId, userId);

        assertNull(fakeTask.getGoal(), "Task must be unlinked (goal = null), not deleted, when its Goal is removed");
        assertNull(fakeHabit.getGoal(), "Habit must be unlinked (goal = null), not deleted, when its Goal is removed");
        verify(goalRepository).delete(fakeGoal);
    }

    @Test
    void getGoalById_WithWrongUserId_ThrowsResourceNotFoundException() {
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                goalService.getGoalById(goalId, userId));
    }

}
