package com.lifesync.api.ai.dto;

import java.util.List;

/**
 * Contexto enviado ao LLM. E minimizado de proposito: sem nome, e-mail,
 * username, senha, UUIDs nem descricoes livres. Os itens sao identificados
 * por indices curtos (T1, H1, G1) que so o servidor sabe mapear de volta.
 * Datas em ISO-8601 (yyyy-MM-dd), no fuso do usuario.
 */
public record UserContextDTO(
        String today,
        String dayOfWeek,
        String timezone,
        List<TaskContext> tasks,
        List<HabitContext> habits,
        List<GoalContext> goals
) {

    public boolean isEmpty() {
        return tasks.isEmpty() && habits.isEmpty() && goals.isEmpty();
    }

    public record TaskContext(
            String shortIndex, // T1, T2...
            String title,
            String priority,
            String status,
            String dueDate,    // null se nao ha prazo
            boolean overdue
    ) {}

    public record HabitContext(
            String shortIndex, // H1, H2...
            String name,
            String frequency,
            int currentStreak, // sequencia efetiva: 0 se o periodo ja foi perdido
            String lastCheckInDate,
            boolean checkedInToday,
            boolean atRisk     // a sequencia ainda vale, mas falta o check-in deste periodo
    ) {}

    public record GoalContext(
            String shortIndex, // G1, G2...
            String title,
            String status,
            double progressPercentage,
            String targetDate
    ) {}
}
