package com.lifesync.api.task.repository;

import com.lifesync.api.task.entity.SubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubTaskRepository extends JpaRepository<SubTask, UUID> {

    List<SubTask> findByTaskId(UUID taskId);

    Optional<SubTask> findByIdAndTaskId(UUID id, UUID taskId);

}
