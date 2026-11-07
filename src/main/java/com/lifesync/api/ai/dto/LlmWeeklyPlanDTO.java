package com.lifesync.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lifesync.api.ai.enums.ReferenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * Saida CRUA do LLM: referencia os itens por indice curto (T1, H1, G1), nunca
 * por id real. Nao e' devolvida ao cliente: o servico a valida (Bean
 * Validation + indices existentes) e a converte em WeeklyPlanResponseDTO. Tudo
 * que vem do modelo e' tratado como entrada nao confiavel, com limites de
 * tamanho para um modelo "falante" nao inflar a resposta.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmWeeklyPlanDTO(
        @NotBlank @Size(max = 1000) String summary,
        @NotNull @Size(max = 30) List<@NotNull @Valid LlmPriority> priorities,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> risks,
        @NotNull @Size(max = 7) Map<DayOfWeek, @NotNull @Size(max = 10) List<@NotBlank @Size(max = 300) String>> schedule
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmPriority(
            @NotNull ReferenceType type,
            @NotBlank @Pattern(regexp = "^[THG][0-9]{1,3}$") String referenceId,
            @NotBlank @Size(max = 500) String reason
    ) {}
}
