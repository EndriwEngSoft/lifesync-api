package com.lifesync.api.task.dto;

import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Traz o SubTaskResponseDTO aninhado, mas nunca a entidade User inteira -
 * so os campos que fazem sentido expor (userId, userName), evitando o
 * mesmo problema de referencia circular que existiria serializando a
 * entidade Task direto (Task -> subTasks -> SubTask -> task -> ...).
 */
@Getter
@Setter
@NoArgsConstructor
public class TaskResponseDTO {

    private UUID id;
    private String title;
    private String description;
    private Priority priority;
    private Status status;
    private LocalDate dueDate;
    private Instant completedAt;


    private UUID userId;
    private String userName;

    private List<SubTaskResponseDTO> subTasks;

    private Instant createdAt;
    private Instant updatedAt;
}