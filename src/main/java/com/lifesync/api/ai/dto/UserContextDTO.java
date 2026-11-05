package com.lifesync.api.ai.dto;

import java.util.List;

public record UserContextDTO(
        List<TaskContext> tasks,
        List<HabitContext> habits,
        List<GoalContext> goals
) {
    public record TaskContext(
            String shortIndex, // Ex: "T1", "T2"
            String title,
            String priority,
            String dueDate
    ) {}

    public record HabitContext(
            String shortIndex, // Ex: "H1", "H2"
            String name,
            String frequency,
            int currentStreak
    ) {}

    public record GoalContext(
            String shortIndex, // Ex: "G1"
            String title,
            String status,
            double progressPercentage,
            String targetDate
    ) {}
}