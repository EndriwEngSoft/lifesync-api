package com.lifesync.api.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usado na criacao (POST /{id}/subtasks) e na edicao de titulo
 * (PUT .../subtasks/{subId}). Nao inclui "completed" de proposito: uma
 * subtask sempre nasce incompleta, e o estado de conclusao so muda via
 * PATCH .../subtasks/{subId} (toggle dedicado), mesmo raciocinio aplicado
 * ao status de Task.
 */
@Getter
@Setter
@NoArgsConstructor
public class SubTaskRequestDTO {

    @NotBlank
    @Size(max = 150)
    private String title;

}
