package com.lifesync.api.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class GoalProgressResponseDTO {

    @Schema(description = "UUID do registro de progresso")
    private UUID id;

    @Schema(description = "Valor acumulado registrado", example = "1500.00")
    private BigDecimal value;

    @Schema(description = "Nota sobre esta atualização")
    private String note;

    @Schema(description = "Momento em que este progresso foi registrado")
    private Instant recordedAt;

}
