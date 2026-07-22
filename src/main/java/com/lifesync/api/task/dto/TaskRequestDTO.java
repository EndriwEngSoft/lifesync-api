package com.lifesync.api.task.dto;

import com.lifesync.api.task.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull
    private Priority priority;

    private LocalDate dueDate;

}
