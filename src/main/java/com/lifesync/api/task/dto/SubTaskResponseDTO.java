package com.lifesync.api.task.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;

import java.util.UUID;

/**
 * userId removido de proposito: uma SubTask sempre aparece aninhada
 * dentro do TaskResponseDTO, que ja carrega esse dado uma vez no nivel
 * de cima - repeti-lo em cada subtask da lista seria redundante.
 */
@Getter
@Setter
@NoArgsConstructor
public class SubTaskResponseDTO {

    @Schema(description = "UUID da subtask")
    private UUID id;

    @Schema(description = "Título da subtask", example = "Comprar o bilhete")
    private String title;

    @Schema(description = "Se está marcada como concluída")
    private boolean completed;

}
