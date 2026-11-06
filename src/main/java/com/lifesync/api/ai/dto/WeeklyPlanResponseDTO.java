package com.lifesync.api.ai.dto;

import com.lifesync.api.ai.enums.ReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plano semanal devolvido pela API. Aqui os itens ja estao com o id REAL
 * (tarefa, habito ou meta do proprio usuario); os indices curtos usados com o
 * modelo nunca saem do servidor.
 */
@Schema(description = "Plano semanal gerado por IA para o usuario autenticado")
public record WeeklyPlanResponseDTO(
        @Schema(description = "Visao geral da semana") String summary,
        @Schema(description = "Itens priorizados, em ordem de importancia") List<PrioritizedItemDTO> priorities,
        @Schema(description = "Riscos identificados para a semana") List<String> risks,
        @Schema(description = "Sugestoes por dia da semana") Map<DayOfWeek, List<String>> schedule
) {

    @Schema(description = "Item priorizado")
    public record PrioritizedItemDTO(
            @Schema(description = "Tipo do item") ReferenceType type,
            @Schema(description = "Id da tarefa, habito ou meta") UUID id,
            @Schema(description = "Motivo da prioridade") String reason
    ) {}
}
