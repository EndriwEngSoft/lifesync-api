package com.lifesync.api.goal.dto;

import com.lifesync.api.goal.enums.GoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * progressPercentage e' calculado em GoalService a partir de
 * currentValue/targetValue, nunca persistido - evita o risco classico de
 * duas fontes de verdade ficarem dessincronizadas.
 */
@Getter
@Setter
@NoArgsConstructor
public class GoalResponseDTO {

    @Schema(description = "UUID da meta")
    private UUID id;

    @Schema(description = "Título da meta", example = "Reserva de emergência")
    private String title;

    @Schema(description = "Descrição da meta", example = "Guardar 6 meses de custo de vida")
    private String description;

    @Schema(description = "Valor atual acumulado", example = "1200.00")
    private BigDecimal currentValue;

    @Schema(description = "Valor alvo", example = "5000.00")
    private BigDecimal targetValue;

    @Schema(description = "Unidade de medida", example = "R$")
    private String unit;

    @Schema(description = "Percentual de progresso, de 0 a 100", example = "24.00")
    private BigDecimal progressPercentage;

    @Schema(description = "Data alvo, se definida", example = "2026-12-31")
    private LocalDate targetDate;

    @Schema(description = "Status da meta", example = "IN_PROGRESS")
    private GoalStatus status;

    @Schema(description = "Data de conclusão, se aplicável")
    private Instant completedAt;

    @Schema(description = "UUID do dono da meta")
    private UUID userId;

    @Schema(description = "Nome do dono da meta")
    private String userName;

    @Schema(description = "UUIDs das tasks vinculadas a esta meta")
    private List<UUID> taskIds;

    @Schema(description = "UUIDs dos hábitos vinculados a esta meta")
    private List<UUID> habitIds;

    @Schema(description = "Data de criação")
    private Instant createdAt;

    @Schema(description = "Data da última atualização")
    private Instant updatedAt;

}
