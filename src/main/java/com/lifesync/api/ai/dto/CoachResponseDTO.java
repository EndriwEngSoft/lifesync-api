package com.lifesync.api.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resposta do assistente de produtividade")
public record CoachResponseDTO(
        @Schema(description = "Resposta em texto") String answer,
        @Schema(description = "Ferramentas de leitura consultadas, na ordem") List<String> toolsUsed,
        @Schema(description = "Chamadas feitas ao modelo") int steps
) {}
