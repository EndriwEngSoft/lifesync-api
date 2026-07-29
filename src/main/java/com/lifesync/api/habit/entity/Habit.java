package com.lifesync.api.habit.entity;

import com.lifesync.api.common.BaseEntity;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_habits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Habit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HabitFrequency frequency;

    @Column(nullable = false)
    private int targetPerPeriod;

    @Builder.Default
    @Column(nullable = false)
    private int currentStreak = 0;

    @Builder.Default
    @Column(nullable = false)
    private int longestStreak = 0;

    /**
     * Soft delete: nunca vira false por delete de verdade, so por
     * HabitService.deleteHabit. Preserva o historico de check-ins mesmo
     * depois do habito ser desativado.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * fetch = LAZY: o padrao do JPA para {@code @ManyToOne} e' EAGER
     * (carregaria o User inteiro toda vez que um Habit e' buscado, mesmo
     * quando nao precisa). So busca o User quando algo de fato chama
     * getUser().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Sem cascade/orphanRemoval de proposito: a exclusao de Habit e
     * soft delete (active = false), entao nunca precisa cascatear
     * remocao de historico.
     */
    @OneToMany(mappedBy = "habit")
    @Builder.Default
    private List<HabitHistory> history = new ArrayList<>();

}
