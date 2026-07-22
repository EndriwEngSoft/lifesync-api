package com.lifesync.api.task.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
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

    private UUID id;
    private String title;
    private boolean completed;

}
