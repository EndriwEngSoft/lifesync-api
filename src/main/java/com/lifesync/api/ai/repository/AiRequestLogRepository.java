package com.lifesync.api.ai.repository;

import com.lifesync.api.ai.entity.AiRequestLog;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {

    /**
     * Quantas chamadas do usuario, para o recurso, com um dos status dados,
     * dentro de [from, to). Sempre escopada por userId. A janela e calculada
     * pelo chamador no fuso do usuario.
     */
    @Query("SELECT COUNT(l) FROM AiRequestLog l WHERE l.userId = :userId AND l.feature = :feature "
            + "AND l.status IN :statuses AND l.createdAt >= :from AND l.createdAt < :to")
    long countUsage(@Param("userId") UUID userId,
                    @Param("feature") AiFeature feature,
                    @Param("statuses") Collection<AiRequestStatus> statuses,
                    @Param("from") Instant from,
                    @Param("to") Instant to);
}
