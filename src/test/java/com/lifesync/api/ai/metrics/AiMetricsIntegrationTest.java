package com.lifesync.api.ai.metrics;

import com.lifesync.api.ai.service.WeeklyPlanService;
import com.lifesync.api.support.ApiTestSupport;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Com o contexto real do Spring: o AiMetrics usa o MeterRegistry da aplicacao
 * (o mesmo que o actuator expoe em /actuator/metrics) e o fluxo completo com
 * cliente fake alimenta as metricas.
 */
@SpringBootTest(properties = {"lifesync.ai.enabled=true", "lifesync.ai.provider=fake", "lifesync.ai.api-key="})
@Import(PostgresTestConfig.class)
@Transactional
class AiMetricsIntegrationTest extends ApiTestSupport {

    @Autowired
    private WeeklyPlanService weeklyPlanService;
    @Autowired
    private MeterRegistry registry;
    @Autowired
    private TaskRepository taskRepository;

    @Test
    void fullFlow_IncrementsTheApplicationMeters() {
        User user = newUser("metricas");
        taskRepository.saveAndFlush(Task.builder().title("tarefa-metricas").priority(Priority.HIGH)
                .status(Status.PENDING).user(user).build());
        double before = successCount();

        weeklyPlanService.generate(user.getId());

        assertThat(successCount()).isEqualTo(before + 1);
        assertThat(registry.get(AiMetrics.TOKENS).tag("direction", "input").counter().count()).isPositive();
        assertThat(registry.get(AiMetrics.REQUEST_DURATION).tag("status", "SUCCESS").timer().count())
                .isPositive();
    }

    @Test
    void userWithoutData_DoesNotRecordAProviderCall() {
        User user = newUser("metricas-vazio");
        double before = successCount();

        weeklyPlanService.generate(user.getId());

        assertThat(successCount()).isEqualTo(before);
    }

    private double successCount() {
        var counter = registry.find(AiMetrics.REQUESTS).tag("status", "SUCCESS").counter();
        return counter == null ? 0 : counter.count();
    }
}
