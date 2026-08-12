package com.lifesync.api.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * O valor informado aqui e' sempre o novo total acumulado, nao um delta
 * a somar - evita ambiguidade sobre "isso e' incremento ou total?" tanto
 * pra quem consome a API quanto pra quem le o codigo depois.
 */
@Getter
@Setter
@NoArgsConstructor
public class GoalProgressRequestDTO {

    @NotNull
    @PositiveOrZero
    @Schema(description = "Novo valor atual acumulado da meta", example = "1500.00")
    private BigDecimal value;

    @Size(max = 500)
    @Schema(description = "Nota opcional sobre esta atualização", example = "Depósito do 13º salário")
    private String note;

}
