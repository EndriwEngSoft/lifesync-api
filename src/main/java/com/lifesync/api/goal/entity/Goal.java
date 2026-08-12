package com.lifesync.api.goal.entity;

import com.lifesync.api.common.BaseEntity;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentValue;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetValue;

    @Column(nullable = false, length = 30)
    private String unit;

    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status;

    private Instant completedAt;

    /**
     * fetch = LAZY seguindo o mesmo padrao de Task.user e Habit.user -
     * so busca o User quando algo de fato chama getUser().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Sem cascade/orphanRemoval de proposito: apagar uma Goal jamais deve
     * arrastar as Tasks vinculadas a ela - mesma filosofia usada em
     * Habit.user (deletar o pai nao deleta o filho). Ao remover a Goal,
     * GoalService desvincula (goal = null) em vez de deletar.
     */
    @OneToMany(mappedBy = "goal")
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "goal")
    @Builder.Default
    private List<Habit> habits = new ArrayList<>();

    /**
     * Aqui SIM cascade ALL + orphanRemoval - diferente de tasks/habits
     * acima. Motivo: GoalProgress e um log que so faz sentido dentro do
     * contexto da Goal (mesma logica de Task -> SubTask), enquanto Task e
     * Habit sao entidades independentes que so referenciam a Goal - elas
     * sobrevivem sem ela.
     */
    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GoalProgress> progressHistory = new ArrayList<>();

}
