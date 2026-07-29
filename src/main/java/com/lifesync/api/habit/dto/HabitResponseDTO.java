package com.lifesync.api.habit.dto;

import com.lifesync.api.habit.enums.HabitFrequency;
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

    private UUID id;
    private String name;
    private String description;

    private HabitFrequency frequency;

    private int targetPerPeriod;
    private int currentStreak;
    private int longestStreak;

    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

}
