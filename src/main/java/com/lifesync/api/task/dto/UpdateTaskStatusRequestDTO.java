package com.lifesync.api.task.dto;

import com.lifesync.api.task.enums.Status;
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
    private Status status;

}
