package com.lifesync.api.habit.repository;

import com.lifesync.api.habit.entity.HabitHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitHistoryRepository extends JpaRepository<HabitHistory, UUID> {

    /**
     * Confere se ja existe check-in nesse dia - segunda camada de defesa
     * contra check-in duplicado, alem da constraint unica no banco.
     */
    boolean existsByHabitIdAndCheckInDate(UUID habitId, LocalDate checkInDate);

    /**
     * O check-in mais recente ANTES da data informada - base do calculo
     * de streak: sem ele, nao teria como saber se o periodo anterior foi
     * cumprido ou se houve uma lacuna.
     */
    Optional<HabitHistory> findTopByHabitIdAndCheckInDateBeforeOrderByCheckInDateDesc(UUID habitId, LocalDate checkInDate);

    Page<HabitHistory> findByHabitId(UUID habitId, Pageable pageable);

}
