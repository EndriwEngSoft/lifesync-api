package com.lifesync.api.task.dto;

import com.lifesync.api.task.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corpo do PATCH /{id}/status. Existe separado de TaskRequestDTO porque
 * mudar status e uma transicao de estado com regra propria, nao um
 * campo qualquer de edicao geral.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateTaskStatusRequestDTO {

    @NotNull
    @Schema(description = "Novo status da task", example = "DONE")
    private Status status;

}
