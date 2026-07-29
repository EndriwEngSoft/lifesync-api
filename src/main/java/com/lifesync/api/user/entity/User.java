package com.lifesync.api.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lifesync.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tb_users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Nunca serializado no JSON. Se essa entidade acabar sendo retornada
     * direto num endpoint (sem passar por DTO), o hash da senha continua
     * protegido mesmo assim.
     */
    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    /**
     * O Builder do Lombok ignora inicializacao de campo por padrao, entao
     * sem essa anotacao {@code User.builder()...build()} sem passar role
     * viria null, nao USER.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    public enum Role {
        USER,
        ADMIN
    }

    /**
     * Mesmo caso do role: sem Builder.Default o builder gera false aqui,
     * ignorando o {@code = true}.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * IANA timezone id (ex: "America/Sao_Paulo"). Usado no calculo de
     * "hoje" no HabitService.checkIn - sem isso, o servidor usaria seu
     * proprio fuso (geralmente UTC em nuvem), o que pode registrar o
     * check-in no dia errado perto da meia-noite local do usuario.
     */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "varchar(255) default 'America/Sao_Paulo'")
    private String timezone = "America/Sao_Paulo";

}