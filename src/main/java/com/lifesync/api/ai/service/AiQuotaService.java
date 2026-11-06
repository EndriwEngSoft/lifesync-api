package com.lifesync.api.ai.service;

import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.repository.AiRequestLogRepository;
import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Cota diaria por usuario, calculada pelo proprio log de chamadas (sem
 * contador separado que possa divergir). "Dia" e o dia no fuso do usuario:
 * a janela vai da meia-noite local ate a meia-noite local seguinte, o que
 * tambem acerta os dias de 23h/25h do horario de verao.
 *
 * Limite conhecido: a checagem e o registro nao sao atomicos; requisicoes
 * simultaneas do mesmo usuario podem ultrapassar a cota em ate o numero de
 * requisicoes paralelas. Aceito: o provedor tem limite proprio e a falha
 * custa pouco (ver docs/ai/decisoes.md).
 */
@Service
public class AiQuotaService {

    private final AiRequestLogRepository logRepository;
    private final UserRepository userRepository;
    private final AiProperties properties;
    private final Clock clock;

    public AiQuotaService(AiRequestLogRepository logRepository, UserRepository userRepository,
                          AiProperties properties, Clock clock) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Lanca AiQuotaExceededException (429) se o usuario ja esgotou a cota de hoje. */
    @Transactional(readOnly = true)
    public void assertWithinQuota(UUID userId, AiFeature feature) {
        long used = usedToday(userId, feature);
        if (used >= properties.dailyQuota()) {
            throw new AiQuotaExceededException("Daily AI quota reached. Try again tomorrow.");
        }
    }

    @Transactional(readOnly = true)
    public long usedToday(UUID userId, AiFeature feature) {
        ZoneId zone = zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        return logRepository.countUsage(userId, feature, AiRequestStatus.COUNTED_FOR_QUOTA, from, to);
    }

    private ZoneId zoneOf(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserZones.of(user);
    }
}
