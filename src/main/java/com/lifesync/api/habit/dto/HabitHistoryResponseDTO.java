package com.lifesync.api.habit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Um item do historico de check-ins de um habito.
 */
@Getter
@Setter
@NoArgsConstructor
public class HabitHistoryResponseDTO {

    @Schema(description = "UUID do registro de histórico")
    private UUID id;

    @Schema(description = "Data do check-in", example = "2026-08-05")
    private LocalDate checkInDate;

    @Schema(description = "Se o check-in foi completado")
    private boolean completed;

}
