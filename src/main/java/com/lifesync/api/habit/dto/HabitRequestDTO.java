package com.lifesync.api.habit.dto;

import com.lifesync.api.habit.enums.HabitFrequency;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    private HabitFrequency frequency;

    @NotNull
    @Min(1)
    private Integer targetPerPeriod;
}
