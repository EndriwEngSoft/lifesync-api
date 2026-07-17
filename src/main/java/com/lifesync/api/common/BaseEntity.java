package com.lifesync.api.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

/**
 * Superclasse mapeada (nao vira tabela propria) com os campos de auditoria
 * comuns a todas as entidades do dominio: data de criacao e ultima atualizacao.
 *
 * Toda entidade deve estender esta classe em vez de declarar seus proprios
 * createdAt/updatedAt — evita duplicacao de coluna e de logica de callback
 * (@PrePersist/@PreUpdate) espalhada pelo projeto.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}