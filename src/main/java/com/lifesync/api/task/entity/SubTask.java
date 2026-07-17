package com.lifesync.api.task.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_sub_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    /**
     * Builder.Default aqui evita que o builder gere esse campo ignorando
     * o {@code = false}. O warning do proprio Maven no build foi o que
     * acusou a falta dessa anotacao.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
}