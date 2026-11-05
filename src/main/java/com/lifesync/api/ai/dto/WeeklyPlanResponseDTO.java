package com.lifesync.api.ai.dto;

import java.util.List;
import java.util.Map;

public record WeeklyPlanResponseDTO(
        String summary,
        List<PrioritizedItemDTO> priorities,
        List<String> risks,
        Map<String, List<String>> schedule
) {
    public record PrioritizedItemDTO(
            String type,
            String referenceId,
            String reason
    ) {}
}