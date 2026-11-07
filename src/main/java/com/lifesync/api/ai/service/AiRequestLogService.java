package com.lifesync.api.ai.service;

import com.lifesync.api.ai.entity.AiRequestLog;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.repository.AiRequestLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Registra uma linha de log por chamada ao LLM. Recebe so metadados: nao ha
 * parametro para texto de prompt ou de resposta, de proposito.
 */
@Service
public class AiRequestLogService {

    private final AiRequestLogRepository repository;

    public AiRequestLogService(AiRequestLogRepository repository) {
        this.repository = repository;
    }

    public AiRequestLog record(UUID userId, AiFeature feature, String promptVersion, String provider,
                               String model, int inputTokens, int outputTokens, long latencyMs,
                               AiRequestStatus status) {
        AiRequestLog log = AiRequestLog.builder()
                .userId(userId)
                .feature(feature)
                .promptVersion(promptVersion)
                .provider(provider)
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .latencyMs(latencyMs)
                .status(status)
                .build();
        return repository.save(log);
    }
}
