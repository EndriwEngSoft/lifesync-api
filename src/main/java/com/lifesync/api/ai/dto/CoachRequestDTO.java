package com.lifesync.api.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Pergunta curta ao assistente de produtividade")
public record CoachRequestDTO(
        @Schema(description = "Pergunta do usuario", example = "O que esta atrasado e o que corre risco esta semana?")
        @NotBlank(message = "Question is required")
        @Size(max = 300, message = "Question must have at most 300 characters")
        String question
) {}
