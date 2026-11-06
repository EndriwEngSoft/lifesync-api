package com.lifesync.api.ai.service;

import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.entity.AiRequestLog;
import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.repository.AiRequestLogRepository;
import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V3 e cota diaria contra um Postgres real (Testcontainers). Cada
 * teste roda numa transacao que e desfeita no fim.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class AiPersistenceIntegrationTest {

    // 2026-11-06T16:00Z: em Sao Paulo (UTC-3) sao 13:00 do dia 06; em Toquio (UTC+9) ja e 01:00 do dia 07.
    private static final Instant NOW = Instant.parse("2026-11-06T16:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AiRequestLogRepository logRepository;
    @Autowired
    private AiRequestLogService logService;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager em;

    // --- migration ---

    @Test
    void migration_CreatesTableFollowingProjectConventions() {
        Map<String, Map<String, Object>> columns = new java.util.HashMap<>();
        jdbc.queryForList("SELECT column_name, data_type, datetime_precision FROM information_schema.columns "
                + "WHERE table_name = 'tb_ai_request_logs'")
                .forEach(row -> columns.put((String) row.get("column_name"), row));

        assertThat(columns.keySet()).contains("id", "created_at", "updated_at", "user_id", "feature",
                "prompt_version", "provider", "model", "input_tokens", "output_tokens", "latency_ms", "status");
        assertThat(columns.get("created_at").get("data_type")).isEqualTo("timestamp without time zone");
        assertThat(columns.get("created_at").get("datetime_precision")).isEqualTo(6);
        assertThat(columns.get("updated_at").get("data_type")).isEqualTo("timestamp without time zone");
    }

    @Test
    void migration_DoesNotLeaveTheOldTableName() {
        Integer old = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ai_request_logs'", Integer.class);
        assertThat(old).isZero();
    }

    @Test
    void migration_CreatesIndexOnUserIdAndCreatedAt() {
        List<String> defs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'tb_ai_request_logs'", String.class);

        assertThat(defs).anyMatch(d -> d.contains("(user_id, created_at)"));
    }

    @Test
    void migration_HasForeignKeyToUsers() {
        Integer fks = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'tb_ai_request_logs' AND constraint_type = 'FOREIGN KEY'", Integer.class);
        assertThat(fks).isEqualTo(1);
    }

    // --- persistencia ---

    @Test
    void record_PersistsMetadataAndAuditColumns() {
        User user = newUser("a", "America/Sao_Paulo");

        AiRequestLog saved = logService.record(user.getId(), AiFeature.WEEKLY_PLAN, "v1.1", "gemini",
                "gemini-test", 120, 80, 950, AiRequestStatus.SUCCESS);
        em.flush();
        em.clear();

        AiRequestLog found = logRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(user.getId());
        assertThat(found.getFeature()).isEqualTo(AiFeature.WEEKLY_PLAN);
        assertThat(found.getStatus()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(found.getInputTokens()).isEqualTo(120);
        assertThat(found.getOutputTokens()).isEqualTo(80);
        assertThat(found.getLatencyMs()).isEqualTo(950);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    // --- cota ---

    @Test
    void usedToday_CountsOnlyTheUsersLocalDay() {
        User sp = newUser("sp", "America/Sao_Paulo");
        // Dia 06 em Sao Paulo = [2026-11-06T03:00Z, 2026-11-07T03:00Z)
        seed(sp, AiRequestStatus.SUCCESS, "2026-11-06T02:59:59Z");   // ontem (23:59 local): fora
        seed(sp, AiRequestStatus.SUCCESS, "2026-11-06T03:00:00Z");   // meia-noite local: dentro
        seed(sp, AiRequestStatus.SUCCESS, "2026-11-07T02:59:59Z");   // 23:59 local: dentro
        seed(sp, AiRequestStatus.SUCCESS, "2026-11-07T03:00:00Z");   // amanha: fora

        assertThat(quota(5).usedToday(sp.getId(), AiFeature.WEEKLY_PLAN)).isEqualTo(2);
    }

    @Test
    void usedToday_SameInstantsCountDifferentlyPerTimezone() {
        User sp = newUser("sp", "America/Sao_Paulo");
        User tokyo = newUser("tk", "Asia/Tokyo");
        // 14:00Z do dia 06 e "hoje" em Sao Paulo (11:00), mas "ontem" em Toquio (23:00 do dia 06)
        seed(sp, AiRequestStatus.SUCCESS, "2026-11-06T14:00:00Z");
        seed(tokyo, AiRequestStatus.SUCCESS, "2026-11-06T14:00:00Z");

        AiQuotaService quota = quota(5);

        assertThat(quota.usedToday(sp.getId(), AiFeature.WEEKLY_PLAN)).isEqualTo(1);
        assertThat(quota.usedToday(tokyo.getId(), AiFeature.WEEKLY_PLAN)).isZero();
    }

    @Test
    void usedToday_IgnoresOtherUsers() {
        User a = newUser("a", "America/Sao_Paulo");
        User b = newUser("b", "America/Sao_Paulo");
        seed(a, AiRequestStatus.SUCCESS, "2026-11-06T12:00:00Z");
        seed(b, AiRequestStatus.SUCCESS, "2026-11-06T12:00:00Z");
        seed(b, AiRequestStatus.SUCCESS, "2026-11-06T13:00:00Z");

        AiQuotaService quota = quota(5);

        assertThat(quota.usedToday(a.getId(), AiFeature.WEEKLY_PLAN)).isEqualTo(1);
        assertThat(quota.usedToday(b.getId(), AiFeature.WEEKLY_PLAN)).isEqualTo(2);
    }

    @Test
    void usedToday_CountsSuccessAndInvalidOutputButNotProviderFailures() {
        User user = newUser("a", "America/Sao_Paulo");
        seed(user, AiRequestStatus.SUCCESS, "2026-11-06T12:00:00Z");
        seed(user, AiRequestStatus.INVALID_OUTPUT, "2026-11-06T12:01:00Z");
        seed(user, AiRequestStatus.PROVIDER_ERROR, "2026-11-06T12:02:00Z");
        seed(user, AiRequestStatus.TIMEOUT, "2026-11-06T12:03:00Z");

        assertThat(quota(5).usedToday(user.getId(), AiFeature.WEEKLY_PLAN)).isEqualTo(2);
    }

    @Test
    void assertWithinQuota_PassesBelowTheLimitAndThrowsAtTheLimit() {
        User user = newUser("a", "America/Sao_Paulo");
        AiQuotaService quota = quota(2);

        seed(user, AiRequestStatus.SUCCESS, "2026-11-06T12:00:00Z");
        quota.assertWithinQuota(user.getId(), AiFeature.WEEKLY_PLAN); // 1 de 2: passa

        seed(user, AiRequestStatus.SUCCESS, "2026-11-06T12:30:00Z");
        assertThatThrownBy(() -> quota.assertWithinQuota(user.getId(), AiFeature.WEEKLY_PLAN))
                .isInstanceOf(AiQuotaExceededException.class);
    }

    @Test
    void assertWithinQuota_AnotherUsersUsageDoesNotBlockMe() {
        User heavy = newUser("heavy", "America/Sao_Paulo");
        User light = newUser("light", "America/Sao_Paulo");
        seed(heavy, AiRequestStatus.SUCCESS, "2026-11-06T12:00:00Z");
        seed(heavy, AiRequestStatus.SUCCESS, "2026-11-06T12:30:00Z");

        AiQuotaService quota = quota(2);

        assertThatThrownBy(() -> quota.assertWithinQuota(heavy.getId(), AiFeature.WEEKLY_PLAN))
                .isInstanceOf(AiQuotaExceededException.class);
        quota.assertWithinQuota(light.getId(), AiFeature.WEEKLY_PLAN);
    }

    @Test
    void invalidStoredTimezone_FallsBackToUtc() {
        User user = newUser("a", "Isto/Nao_Existe");
        // Em UTC o dia 06 vai de 00:00Z a 24:00Z
        seed(user, AiRequestStatus.SUCCESS, "2026-11-06T00:30:00Z");
        seed(user, AiRequestStatus.SUCCESS, "2026-11-05T23:30:00Z");

        assertThat(quota(5).usedToday(user.getId(), AiFeature.WEEKLY_PLAN)).isEqualTo(1);
    }

    @Test
    void unknownUser_IsNotFound() {
        assertThatThrownBy(() -> quota(5).usedToday(UUID.randomUUID(), AiFeature.WEEKLY_PLAN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- apoio ---

    private AiQuotaService quota(int dailyQuota) {
        AiProperties props = new AiProperties(true, "fake", "", "http://localhost", "m", Duration.ofSeconds(5),
                Duration.ofSeconds(5), 100, dailyQuota, 3, Duration.ZERO);
        return new AiQuotaService(logRepository, userRepository, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User newUser(String tag, String timezone) {
        String unique = tag + "-" + UUID.randomUUID();
        return userRepository.saveAndFlush(User.builder()
                .name("Nome " + tag).username(unique).email(unique + "@teste.com")
                .passwordHash("hash").timezone(timezone).build());
    }

    /** Grava um log e ajusta created_at pelo mesmo caminho de mapeamento do Hibernate. */
    private void seed(User user, AiRequestStatus status, String createdAtUtc) {
        AiRequestLog log = logService.record(user.getId(), AiFeature.WEEKLY_PLAN, "v1.1", "fake", "m",
                1, 1, 1, status);
        em.flush();
        em.createNativeQuery("UPDATE tb_ai_request_logs SET created_at = :ts WHERE id = :id")
                .setParameter("ts", Instant.parse(createdAtUtc))
                .setParameter("id", log.getId())
                .executeUpdate();
        em.clear();
    }
}
