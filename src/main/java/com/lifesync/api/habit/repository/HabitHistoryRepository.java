package com.lifesync.api.habit.repository;

import com.lifesync.api.habit.entity.HabitHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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

    /**
     * Data do check-in mais recente de cada habito informado, numa consulta
     * so. Escopada pelo dono (h.habit.user.id) alem da lista de ids, para
     * nunca devolver historico de habito de outro usuario.
     */
    @Query("SELECT h.habit.id AS habitId, MAX(h.checkInDate) AS lastCheckInDate FROM HabitHistory h "
            + "WHERE h.habit.user.id = :userId AND h.habit.id IN :habitIds GROUP BY h.habit.id")
    List<LastCheckIn> findLastCheckIns(@Param("userId") UUID userId, @Param("habitIds") Collection<UUID> habitIds);

    /** Projecao de findLastCheckIns. */
    interface LastCheckIn {
        UUID getHabitId();

        LocalDate getLastCheckInDate();
    }

}
