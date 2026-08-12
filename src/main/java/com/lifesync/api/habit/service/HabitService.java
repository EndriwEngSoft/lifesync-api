package com.lifesync.api.habit.service;

import com.lifesync.api.exception.DuplicateResourceException;
import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.dto.HabitHistoryResponseDTO;
import com.lifesync.api.habit.dto.HabitRequestDTO;
import com.lifesync.api.habit.dto.HabitResponseDTO;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.entity.HabitHistory;
import com.lifesync.api.habit.repository.HabitHistoryRepository;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Toda consulta/alteracao e escopada pelo userId de quem esta autenticado,
 * nunca busca por id sozinho - mesma defesa contra acesso indevido usada
 * no modulo de tarefas (findByIdAndUserId devolve vazio, nunca o habito
 * de outro usuario, e isso vira 404 via ResourceNotFoundException).
 *
 * targetPerPeriod hoje e apenas informativo: o streak considera presenca
 * no periodo (dia/semana/mes), nao quantas vezes o usuario marcou dentro
 * dele. Calcular streak por meta batida exigiria contar check-ins dentro
 * de cada intervalo em vez de olhar so o mais recente - deixado de fora
 * de proposito nesta versao.
 */
@RequiredArgsConstructor
@Service
public class HabitService {

    private static final String HABIT_NOT_FOUND = "Habit not found";

    private final HabitRepository habitRepository;
    private final HabitHistoryRepository habitHistoryRepository;
    private final GoalRepository goalRepository;
    private final UserService userService;

    /**
     * Todo campo derivado de estado (currentStreak, longestStreak, active)
     * fica de fora do request de proposito - nasce sempre com o default
     * do construtor, e so muda via checkIn/deleteHabit.
     */
    @Transactional
    public HabitResponseDTO createHabit(HabitRequestDTO request, UUID userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = new Habit();
        habit.setUser(user);
        habit.setName(request.getName());
        habit.setDescription(request.getDescription());
        habit.setFrequency(request.getFrequency());
        habit.setTargetPerPeriod(request.getTargetPerPeriod());
        habit.setGoal(resolveGoal(request.getGoalId(), userId));

        Habit savedHabit = habitRepository.save(habit);
        return toResponseDTO(savedHabit);
    }

    /**
     * findByIdAndUserId ja e a defesa de acesso: se o habito nao
     * pertencer a esse userId, a query devolve vazio e o orElseThrow
     * dispara antes de qualquer coisa.
     */
    @Transactional(readOnly = true)
    public HabitResponseDTO getHabitById(UUID habitId, UUID userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(HABIT_NOT_FOUND));

        return toResponseDTO(habit);
    }

    @Transactional(readOnly = true)
    public Page<HabitResponseDTO> getAllHabits(UUID userId, Pageable pageable) {
        Page<Habit> habits = habitRepository.findByUserId(userId, pageable);
        return habits.map(this::toResponseDTO);
    }

    /**
     * Edicao geral (nome, descricao, frequencia, meta). Nao mexe em
     * currentStreak/longestStreak/active de proposito - esses campos so
     * mudam via checkIn ou deleteHabit - EXCETO currentStreak quando a
     * frequencia muda.
     *
     * Motivo: isConsecutiveCheckIn decide se um check-in e consecutivo
     * comparando com o periodo anterior segundo a frequencia ATUAL do
     * habito. Um streak acumulado sob DAILY nao tem significado nenhum se
     * o proximo check-in for avaliado sob as regras de WEEKLY ou MONTHLY -
     * a comparacao ficaria matematicamente incoerente (pode tanto zerar
     * um streak valido quanto, em tese, contar como consecutivo algo que
     * nao era). Resetar currentStreak pra 0 na troca de frequencia evita
     * esse caso e deixa o comportamento previsivel: trocar a frequencia
     * comeca uma nova janela de streak. longestStreak NAO e afetado -
     * e um recorde historico, nao um contador em andamento, e a troca de
     * frequencia nao apaga uma conquista ja registrada.
     */
    @Transactional
    public HabitResponseDTO updateHabit(UUID habitId, HabitRequestDTO request, UUID userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(HABIT_NOT_FOUND));

        if (habit.getFrequency() != request.getFrequency()) {
            habit.setCurrentStreak(0);
        }

        habit.setName(request.getName());
        habit.setDescription(request.getDescription());
        habit.setFrequency(request.getFrequency());
        habit.setTargetPerPeriod(request.getTargetPerPeriod());
        habit.setGoal(resolveGoal(request.getGoalId(), userId));

        Habit savedHabit = habitRepository.save(habit);
        return toResponseDTO(savedHabit);
    }

    /**
     * Soft delete (active = false) em vez de apagar de verdade - apagar
     * perderia todo o historico de check-ins, que continua tendo valor
     * mesmo depois do habito ser desativado (analises futuras, por
     * exemplo). O historico e os check-ins antigos continuam no banco.
     */
    @Transactional
    public void deleteHabit(UUID habitId, UUID userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(HABIT_NOT_FOUND));

        habit.setActive(false);
        habitRepository.save(habit);
    }

    /**
     * goalId nulo desvincula (retorna null); nao-nulo precisa pertencer
     * ao mesmo userId, senao 404 - mesma logica usada em
     * TaskService.resolveGoal.
     */
    private Goal resolveGoal(UUID goalId, UUID userId) {
        if (goalId == null) {
            return null;
        }
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
    }

    @Transactional(readOnly = true)
    public Page<HabitHistoryResponseDTO> getHistory(UUID habitId, UUID userId,  Pageable pageable) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(HABIT_NOT_FOUND));

        Page<HabitHistory> historyPage = habitHistoryRepository.findByHabitId(habit.getId(), pageable);
        return historyPage.map(this::toResponseDTO);
    }

    /**
     * Algoritmo de streak: busca o check-in mais recente antes de hoje;
     * se nao existir nenhum, streak = 1; se existir e for o periodo
     * imediatamente anterior (dia, semana ou mes, conforme a frequencia
     * do habito), streak += 1; se houver uma lacuna, streak volta a 1.
     * "Hoje" e calculado no fuso horario do dono do habito, nao do
     * servidor - sem isso, o calculo usaria o fuso de onde a aplicacao
     * roda (frequentemente UTC em nuvem), podendo classificar o check-in
     * no dia errado perto da meia-noite local do usuario. Um segundo
     * check-in no mesmo dia e bloqueado em duas camadas: a checagem
     * "existsBy..." acima (rapido, cobre o caso comum) e a constraint
     * unica do banco, capturada aqui como DataIntegrityViolationException
     * - essa segunda camada existe porque a checagem sozinha tem uma
     * condicao de corrida (duas requisicoes simultaneas passariam pela
     * checagem antes de qualquer uma salvar). saveAndFlush força o
     * INSERT a acontecer agora, dentro do try, em vez de so no commit
     * da transacao - senao a violacao de constraint escaparia pro
     * catch-all generico, fora deste metodo.
     */
    @Transactional
    public HabitResponseDTO checkIn(UUID habitId, UUID userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(HABIT_NOT_FOUND));

        if (!habit.isActive()) {
            throw new ResourceNotFoundException(HABIT_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(ZoneId.of(habit.getUser().getTimezone()));

        if (habitHistoryRepository.existsByHabitIdAndCheckInDate(habitId, today)) {
            throw new DuplicateResourceException("Habit already checked in for today");
        }

        HabitHistory previousHistory = habitHistoryRepository
                .findTopByHabitIdAndCheckInDateBeforeOrderByCheckInDateDesc(habitId, today)
                .orElse(null);

        int currentStreak;
        if (previousHistory == null) {
            currentStreak = 1;
        } else if (isConsecutiveCheckIn(habit, previousHistory.getCheckInDate(), today)) {
            currentStreak = habit.getCurrentStreak() + 1;
        } else {
            currentStreak = 1;
        }

        habit.setCurrentStreak(currentStreak);
        if (currentStreak > habit.getLongestStreak()) {
            habit.setLongestStreak(currentStreak);
        }

        Habit savedHabit = habitRepository.save(habit);

        HabitHistory habitHistory = new HabitHistory();
        habitHistory.setHabit(savedHabit);
        habitHistory.setCheckInDate(today);
        habitHistory.setCompleted(true);
        try {
            habitHistoryRepository.saveAndFlush(habitHistory);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("Habit already checked in for today");
        }

        return toResponseDTO(savedHabit);
    }

    private HabitResponseDTO toResponseDTO(Habit habit) {
        HabitResponseDTO dto = new HabitResponseDTO();

        dto.setId(habit.getId());
        dto.setName(habit.getName());
        dto.setDescription(habit.getDescription());
        dto.setFrequency(habit.getFrequency());
        dto.setTargetPerPeriod(habit.getTargetPerPeriod());
        dto.setCurrentStreak(habit.getCurrentStreak());
        dto.setLongestStreak(habit.getLongestStreak());
        dto.setActive(habit.isActive());
        dto.setGoalId(habit.getGoal() != null ? habit.getGoal().getId() : null);
        dto.setCreatedAt(habit.getCreatedAt());
        dto.setUpdatedAt(habit.getUpdatedAt());

        return dto;
    }

    private HabitHistoryResponseDTO toResponseDTO(HabitHistory history) {
        HabitHistoryResponseDTO dto = new HabitHistoryResponseDTO();

        dto.setId(history.getId());
        dto.setCheckInDate(history.getCheckInDate());
        dto.setCompleted(history.isCompleted());

        return dto;
    }

    /**
     * DAILY compara contra o dia anterior; WEEKLY e MONTHLY nao comparam
     * dia a dia, comparam o periodo (a semana ou o mes anterior) -
     * ver isConsecutiveWeekly para o motivo de precisar de um metodo
     * separado so pra semana.
     */
    private boolean isConsecutiveCheckIn(Habit habit, LocalDate previousDate, LocalDate today) {
        return switch (habit.getFrequency()) {
            case DAILY -> previousDate.plusDays(1).equals(today);
            case WEEKLY -> isConsecutiveWeekly(previousDate, today);
            case MONTHLY -> YearMonth.from(previousDate).plusMonths(1).equals(YearMonth.from(today));
        };
    }

    /**
     * Truncar as duas datas pro inicio da semana (segunda-feira) antes
     * de comparar - assim um check-in na sexta e outro na segunda
     * seguinte contam como semanas consecutivas, mesmo sem serem dias
     * adjacentes.
     */
    private boolean isConsecutiveWeekly(LocalDate previousDate, LocalDate today) {
        LocalDate previousWeekStart = previousDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return previousWeekStart.plusWeeks(1).equals(currentWeekStart);
    }

}
