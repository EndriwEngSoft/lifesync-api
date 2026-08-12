package com.lifesync.api.habit.dto;

import com.lifesync.api.habit.enums.HabitFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Formato de resposta de um habito, incluindo o streak atual e o recorde.
 */
@Getter
@Setter
@NoArgsConstructor
public class HabitResponseDTO {

    @Schema(description = "UUID do hábito")
    private UUID id;

    @Schema(description = "Nome do hábito", example = "Beber 2L de água")
    private String name;

    @Schema(description = "Descrição do hábito", example = "Tomar oito copos de água por dia")
    private String description;

    @Schema(description = "Frequência do hábito", example = "DAILY")
    private HabitFrequency frequency;

    @Schema(description = "Meta por período")
    private int targetPerPeriod;

    @Schema(description = "Streak atual")
    private int currentStreak;

    @Schema(description = "Maior streak registrado")
    private int longestStreak;

    @Schema(description = "Se está ativo (não deletado)")
    private boolean active;

    @Schema(description = "UUID da meta vinculada, se houver")
    private UUID goalId;

    @Schema(description = "Data de criação")
    private Instant createdAt;

    @Schema(description = "Data da última atualização")
    private Instant updatedAt;

}
