package com.lifesync.api.task.entity;

import com.lifesync.api.common.BaseEntity;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private LocalDate dueDate;

    private Instant completedAt;

    /**
     * fetch = LAZY: o padrao do JPA para {@code @ManyToOne} e' EAGER
     * (carregaria o User inteiro toda vez que uma Task e' buscada, mesmo
     * quando nao precisa). LAZY so busca o User quando algo de fato chama
     * task.getUser() - e como TaskService roda dentro de
     * {@code @Transactional}, isso continua funcionando normalmente
     * (sessao ainda aberta). Mesmo padrao usado em Habit.user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Opcional (nullable): uma Task pode existir sem estar vinculada a
     * nenhuma Goal. Sem cascade - deletar a Goal nunca deve deletar a
     * Task, so desvincula-la (goal = null), feito em GoalService.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    /**
     * Cascade ALL + orphanRemoval porque uma SubTask nao existe fora da
     * Task pai — se a Task some, as subtasks somem junto. Repare que
     * Task -> User nao tem cascade nenhum: apagar/desativar um User
     * jamais deveria arrastar as Tasks dele em cascata.
     */
    @OneToMany(
            mappedBy = "task",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<SubTask> subTasks = new ArrayList<>();
}