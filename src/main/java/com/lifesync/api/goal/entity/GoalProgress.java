package com.lifesync.api.goal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot do progresso de uma Goal num instante. Diferente do
 * HabitHistory, nao ha constraint de unicidade por data: uma meta pode
 * receber varias atualizacoes de progresso no mesmo dia, entao
 * recordedAt e' Instant (com hora), nao LocalDate.
 */
@Entity
@Table(name = "tb_goal_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    /**
     * Valor absoluto de currentValue no momento do registro (nao um
     * delta) - simplifica montar um grafico de evolucao depois: basta
     * plotar value x recordedAt em ordem cronologica.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private Instant recordedAt;

}
