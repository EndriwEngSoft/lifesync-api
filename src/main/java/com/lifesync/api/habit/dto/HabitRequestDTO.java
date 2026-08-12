package com.lifesync.api.habit.dto;

import com.lifesync.api.habit.enums.HabitFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Usado no POST e no PUT de habito. Sem currentStreak/longestStreak/active
 * de proposito - sao campos derivados, controlados pelo servidor (via
 * checkIn e deleteHabit), nunca preenchidos pelo cliente.
 */
@Getter
@Setter
@NoArgsConstructor
public class HabitRequestDTO {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Nome do hábito", example = "Beber 2L de água")
    private String name;

    @Size(max = 2000)
    @Schema(description = "Descrição do hábito", example = "Tomar oito copos de água por dia")
    private String description;

    @NotNull
    @Schema(description = "Frequência do hábito", example = "DAILY")
    private HabitFrequency frequency;

    @NotNull
    @Min(1)
    @Schema(description = "Meta por período", example = "1")
    private Integer targetPerPeriod;

    @Schema(description = "UUID da meta vinculada (opcional)")
    private UUID goalId;
}
