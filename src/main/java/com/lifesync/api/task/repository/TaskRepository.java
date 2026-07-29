package com.lifesync.api.task.repository;

import com.lifesync.api.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Task> findByUserId(UUID userId, Pageable pageable);
}