package com.lifesync.api.task.repository;

import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * Escopada pelo dono de proposito: se a task nao pertencer a esse
     * userId, devolve vazio - nunca a task de outro usuario.
     */
    Optional<Task> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Filtro combinado por status e priority, ambos opcionais.
     * (:status IS NULL OR t.status = :status) evita ter que escrever uma
     * combinacao de query methods pra cada par de filtro presente/ausente
     * (findByUserId, findByUserIdAndStatus, findByUserIdAndPriority,
     * findByUserIdAndStatusAndPriority) - um metodo so cobre os quatro
     * casos. Continua uma query so, sem N+1: o filtro entra no WHERE,
     * nao em memoria depois de carregar tudo.
     */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByUserIdWithFilters(@Param("userId") UUID userId,
                                        @Param("status") Status status,
                                        @Param("priority") Priority priority,
                                        Pageable pageable);
}