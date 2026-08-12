package com.lifesync.api.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Usado tanto na criacao (POST) quanto na edicao geral (PUT) de uma Goal.
 * Nao inclui currentValue nem status de proposito: toda Goal nasce com
 * currentValue = 0 e status = IN_PROGRESS - progresso e atualizado via
 * endpoint dedicado (ver GoalProgressRequestDTO), nao editado livremente
 * aqui, senao perderiamos o historico em tb_goal_progress.
 */
@Getter
@Setter
@NoArgsConstructor
public class GoalRequestDTO {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Título da meta", example = "Reserva de emergência")
    private String title;

    @Size(max = 2000)
    @Schema(description = "Descrição detalhada da meta", example = "Guardar 6 meses de custo de vida")
    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "targetValue deve ser maior que zero")
    @Schema(description = "Valor alvo a ser atingido", example = "5000.00")
    private BigDecimal targetValue;

    @NotBlank
    @Size(max = 30)
    @Schema(description = "Unidade de medida do progresso", example = "R$")
    private String unit;

    @Schema(description = "Data alvo para concluir a meta (opcional)", example = "2026-12-31")
    private LocalDate targetDate;

}
