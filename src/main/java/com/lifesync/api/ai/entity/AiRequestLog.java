package com.lifesync.api.ai.entity;

import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Uma linha por chamada ao LLM. So metadados, nunca o texto do prompt nem da
 * resposta (ver docs/ai/decisoes.md). O dono e guardado como userId simples:
 * o log so e escrito e contado, nunca navegado a partir do usuario.
 */
@Entity
@Table(name = "tb_ai_request_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRequestLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiFeature feature;

    @Column(nullable = false, length = 20)
    private String promptVersion;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private int inputTokens;

    @Column(nullable = false)
    private int outputTokens;

    @Column(nullable = false)
    private long latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiRequestStatus status;
}
