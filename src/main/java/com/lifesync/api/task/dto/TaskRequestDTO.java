package com.lifesync.api.task.dto;

import com.lifesync.api.task.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Usado tanto na criacao (POST) quanto na edicao geral (PUT) de uma Task.
 * Nao inclui "status" de proposito: mudar status e uma transicao de
 * estado com regra propria (ver UpdateTaskStatusRequestDTO), nao um campo
 * livre editavel por qualquer PUT. Toda Task nasce com status PENDING.
 */
@Getter
@Setter
@NoArgsConstructor
public class TaskRequestDTO {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Título da tarefa", example = "Pagar contas de luz")
    private String title;

    @Size(max = 2000)
    @Schema(description = "Descrição detalhada da tarefa", example = "Pagar a conta de luz até dia 10")
    private String description;

    @NotNull
    @Schema(description = "Prioridade da tarefa", example = "MEDIUM")
    private Priority priority;

    @Schema(description = "Data de vencimento (opcional)", example = "2026-12-31")
    private LocalDate dueDate;

}
