package com.lifesync.api.habit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Log de check-ins de um Habit. A constraint unica em (habit_id,
 * check_in_date) impede dois check-ins no mesmo dia pro mesmo habito -
 * sem ela, nada impediria inflar o streak marcando o mesmo habito varias
 * vezes num so dia.
 */
@Entity
@Table(name = "tb_habit_history", uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "check_in_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    /**
     * Hoje sempre true: o unico fluxo que cria um {@code HabitHistory} e
     * HabitService.checkIn, que so roda quando o usuario efetivamente
     * marca o habito como feito. Registrar um periodo "perdido"
     * ({@code completed = false}) exigiria um job agendado detectando
     * ausencia de check-in num periodo encerrado - fora do escopo deste
     * projeto por decisao consciente, nao por limitacao tecnica. O campo
     * ja existe pronto pra esse caso caso o escopo mude no futuro, o que
     * evita uma migracao de schema adicional; ate la, e uma constante.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean completed = true;

}
