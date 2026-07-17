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

    // @Builder.Default: sem essa anotacao, @Builder ignora o "= false"
    // abaixo e gera boolean com o default puro do Java (que tambem seria
    // false aqui, mas o alerta do proprio Maven no build pedia a anotacao
    // explicita para deixar a intencao clara).
    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
}