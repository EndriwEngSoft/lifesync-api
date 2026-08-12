package com.lifesync.api.task.dto;

import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

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

    @Schema(description = "UUID da task")
    private UUID id;

    @Schema(description = "Título da task", example = "Comprar presente")
    private String title;

    @Schema(description = "Descrição da task", example = "Comprar presente de aniversário para Maria")
    private String description;

    @Schema(description = "Prioridade da task", example = "MEDIUM")
    private Priority priority;

    @Schema(description = "Status da task", example = "PENDING")
    private Status status;

    @Schema(description = "Data de vencimento", example = "2026-12-01")
    private LocalDate dueDate;

    @Schema(description = "Data de conclusão, se aplicável")
    private Instant completedAt;


    @Schema(description = "UUID do dono da task")
    private UUID userId;

    @Schema(description = "Nome do dono da task")
    private String userName;

    @Schema(description = "Subtasks aninhadas")
    private List<SubTaskResponseDTO> subTasks;

    @Schema(description = "UUID da meta vinculada, se houver")
    private UUID goalId;

    @Schema(description = "Data de criação")
    private Instant createdAt;

    @Schema(description = "Data da última atualização")
    private Instant updatedAt;
}