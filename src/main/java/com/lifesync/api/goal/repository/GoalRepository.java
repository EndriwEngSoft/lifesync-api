package com.lifesync.api.goal.repository;

import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.enums.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /**
     * Escopada pelo dono de proposito: se a goal nao pertencer a esse
     * userId, devolve vazio - nunca a goal de outro usuario.
     */
    Optional<Goal> findByIdAndUserId(UUID goalId, UUID userId);

    /**
     * Filtro opcional por status, mesmo padrao usado em
     * TaskRepository.findByUserIdWithFilters.
     */
    @Query("SELECT g FROM Goal g WHERE g.user.id = :userId "
            + "AND (:status IS NULL OR g.status = :status)")
    Page<Goal> findByUserIdWithFilters(@Param("userId") UUID userId,
                                        @Param("status") GoalStatus status,
                                        Pageable pageable);

}
