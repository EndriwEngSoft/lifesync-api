package com.lifesync.api.habit.dto;

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

    private UUID id;

    private LocalDate checkInDate;

    private boolean completed;

}
