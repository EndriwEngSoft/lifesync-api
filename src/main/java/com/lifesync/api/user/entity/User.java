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

    // @JsonIgnore garante que o hash da senha nunca seja serializado numa
    // resposta JSON, mesmo se essa entidade for retornada direto (sem DTO)
    // por engano em algum endpoint futuro.
    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    // @Builder.Default e obrigatorio aqui: o Lombok @Builder ignora valores
    // de inicializacao de campo por padrao. Sem essa anotacao, User.builder()
    // sem informar role/active geraria null/false silenciosamente.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    public enum Role {
        USER,
        ADMIN
    }

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

}