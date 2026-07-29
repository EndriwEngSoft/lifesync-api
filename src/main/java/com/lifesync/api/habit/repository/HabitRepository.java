package com.lifesync.api.habit.repository;

import com.lifesync.api.habit.entity.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitRepository extends JpaRepository<Habit, UUID> {

    /**
     * Escopada pelo dono de proposito: se o habito nao pertencer a esse
     * userId, devolve vazio - nunca o habito de outro usuario.
     */
    Optional<Habit> findByIdAndUserId(UUID habitId, UUID userId);

    Page<Habit> findByUserId(UUID userId, Pageable pageable);

}
