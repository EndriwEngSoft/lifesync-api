package com.lifesync.api.goal.service;

import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.goal.dto.GoalProgressRequestDTO;
import com.lifesync.api.goal.dto.GoalProgressResponseDTO;
import com.lifesync.api.goal.dto.GoalRequestDTO;
import com.lifesync.api.goal.dto.GoalResponseDTO;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.entity.GoalProgress;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.goal.repository.GoalProgressRepository;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Toda consulta/alteracao de Goal e escopada pelo userId de quem esta
 * autenticado - mesma defesa contra IDOR usada em Task e Habit
 * (findByIdAndUserId devolve vazio, nunca a goal de outro usuario).
 */
@RequiredArgsConstructor
@Service
public class GoalService {

    private static final String GOAL_NOT_FOUND = "Goal not found";

    private final GoalRepository goalRepository;
    private final GoalProgressRepository goalProgressRepository;
    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final UserService userService;

    /**
     * Toda Goal nasce com currentValue = 0 e status = IN_PROGRESS - esses
     * dois campos nao sao editaveis por request de criacao/edicao,
     * so mudam via recordProgress.
     */
    @Transactional
    public GoalResponseDTO createGoal(GoalRequestDTO request, UUID userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setTitle(request.getTitle());
        goal.setDescription(request.getDescription());
        goal.setTargetValue(request.getTargetValue());
        goal.setUnit(request.getUnit());
        goal.setTargetDate(request.getTargetDate());
        goal.setCurrentValue(BigDecimal.ZERO);
        goal.setStatus(GoalStatus.IN_PROGRESS);

        Goal savedGoal = goalRepository.save(goal);
        return toResponseDTO(savedGoal);
    }

    @Transactional(readOnly = true)
    public GoalResponseDTO getGoalById(UUID goalId, UUID userId) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND));

        return toResponseDTO(goal);
    }

    @Transactional(readOnly = true)
    public Page<GoalResponseDTO> getAllGoals(UUID userId, GoalStatus status, Pageable pageable) {
        Page<Goal> goals = goalRepository.findByUserIdWithFilters(userId, status, pageable);
        return goals.map(this::toResponseDTO);
    }

    /**
     * Edicao geral (titulo, descricao, targetValue, unit, targetDate).
     * Nao mexe em currentValue/status de proposito - ver recordProgress.
     */
    @Transactional
    public GoalResponseDTO updateGoal(UUID goalId, GoalRequestDTO request, UUID userId) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND));

        goal.setTitle(request.getTitle());
        goal.setDescription(request.getDescription());
        goal.setTargetValue(request.getTargetValue());
        goal.setUnit(request.getUnit());
        goal.setTargetDate(request.getTargetDate());

        Goal savedGoal = goalRepository.save(goal);
        return toResponseDTO(savedGoal);
    }

    /**
     * Task e Habit nao tem cascade a partir de Goal (ver Goal.tasks /
     * Goal.habits) - de proposito, pra apagar uma Goal nunca arrastar o
     * que esta vinculado a ela. Por isso, antes do delete, cada Task e
     * cada Habit vinculados sao desvinculados (goal = null) explicitamente
     * aqui. GoalProgress, ao contrario, tem cascade ALL + orphanRemoval
     * em Goal.progressHistory - o delete dele acontece automatico.
     */
    @Transactional
    public void deleteGoal(UUID goalId, UUID userId) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND));

        goal.getTasks().forEach(task -> task.setGoal(null));
        taskRepository.saveAll(goal.getTasks());

        goal.getHabits().forEach(habit -> habit.setGoal(null));
        habitRepository.saveAll(goal.getHabits());

        goalRepository.delete(goal);
    }

    /**
     * O valor informado e sempre o novo total acumulado (nao um delta -
     * ver GoalProgressRequestDTO). Cada chamada gera um snapshot em
     * GoalProgress, preservando o historico de evolucao mesmo depois da
     * Goal ja ter mudado varias vezes - sem isso, so seria possivel saber
     * o valor atual, nunca como ele chegou la (relevante pro Dashboard,
     * mais adiante no roadmap).
     *
     * Por design, um valor MENOR que o currentValue atual e aceito sem
     * bloqueio - retroceder o progresso e uma operacao legitima (ex:
     * usuario corrigindo um lancamento errado, ou uma meta cujo total
     * diminuiu de verdade), nao um caso de erro a ser rejeitado. Nao ha
     * validacao "so aceita >= currentValue" de proposito.
     *
     * Quando o novo valor atinge ou ultrapassa o alvo e a goal ainda
     * esta em andamento, o status muda automaticamente pra COMPLETED e
     * completedAt e preenchido - mesma logica de completedAt em Task,
     * so que disparada por valor em vez de uma transicao de status
     * explicita.
     */
    @Transactional
    public GoalResponseDTO recordProgress(UUID goalId, UUID userId, GoalProgressRequestDTO request) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND));

        goal.setCurrentValue(request.getValue());

        if (goal.getStatus() == GoalStatus.IN_PROGRESS
                && goal.getCurrentValue().compareTo(goal.getTargetValue()) >= 0) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(Instant.now());
        }

        Goal savedGoal = goalRepository.save(goal);

        GoalProgress progress = GoalProgress.builder()
                .goal(savedGoal)
                .value(request.getValue())
                .note(request.getNote())
                .recordedAt(Instant.now())
                .build();
        goalProgressRepository.save(progress);

        return toResponseDTO(savedGoal);
    }

    @Transactional(readOnly = true)
    public List<GoalProgressResponseDTO> getProgressHistory(UUID goalId, UUID userId) {
        goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND));

        return goalProgressRepository.findByGoalIdOrderByRecordedAtDesc(goalId).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    private GoalResponseDTO toResponseDTO(Goal goal) {
        GoalResponseDTO dto = new GoalResponseDTO();
        dto.setId(goal.getId());
        dto.setTitle(goal.getTitle());
        dto.setDescription(goal.getDescription());
        dto.setCurrentValue(goal.getCurrentValue());
        dto.setTargetValue(goal.getTargetValue());
        dto.setUnit(goal.getUnit());
        dto.setProgressPercentage(calculateProgressPercentage(goal));
        dto.setTargetDate(goal.getTargetDate());
        dto.setStatus(goal.getStatus());
        dto.setCompletedAt(goal.getCompletedAt());
        dto.setUserId(goal.getUser().getId());
        dto.setUserName(goal.getUser().getName());
        dto.setTaskIds(goal.getTasks().stream().map(t -> t.getId()).collect(Collectors.toList()));
        dto.setHabitIds(goal.getHabits().stream().map(h -> h.getId()).collect(Collectors.toList()));
        dto.setCreatedAt(goal.getCreatedAt());
        dto.setUpdatedAt(goal.getUpdatedAt());
        return dto;
    }

    /**
     * targetValue e validado como > 0 no DTO (@DecimalMin), entao a
     * divisao aqui nunca e por zero. min(..., 100) evita mostrar
     * "120% concluido" quando currentValue ultrapassa o alvo.
     */
    private BigDecimal calculateProgressPercentage(Goal goal) {
        BigDecimal raw = goal.getCurrentValue()
                .divide(goal.getTargetValue(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return raw.min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private GoalProgressResponseDTO toResponseDTO(GoalProgress progress) {
        GoalProgressResponseDTO dto = new GoalProgressResponseDTO();
        dto.setId(progress.getId());
        dto.setValue(progress.getValue());
        dto.setNote(progress.getNote());
        dto.setRecordedAt(progress.getRecordedAt());
        return dto;
    }

}
