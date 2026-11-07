package com.lifesync.api.ai.metrics;

import com.lifesync.api.ai.enums.AiFeature;
import com.lifesync.api.ai.enums.AiRequestStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metricas do modulo de IA (Micrometer). Expostas em /actuator/metrics, que
 * exige token.
 *
 * - lifesync.ai.request.duration (Timer): duracao da chamada ao provedor; a
 *   contagem do proprio Timer e' a contagem de chamadas por status;
 * - lifesync.ai.requests (Counter): chamadas por status (SUCCESS, PROVIDER_ERROR,
 *   INVALID_OUTPUT, TIMEOUT);
 * - lifesync.ai.tokens (Counter): tokens de entrada e de saida (tag direction);
 * - lifesync.ai.rejections (Counter): pedidos barrados antes do provedor
 *   (DISABLED, QUOTA_EXCEEDED).
 *
 * Privacidade: as unicas tags sao valores de enums e o nome do provedor vindo
 * da configuracao. Esta classe nem recebe o texto do prompt, a resposta do
 * modelo, o id do usuario nem a chave: o que nao entra nao pode vazar.
 */
@Component
public class AiMetrics {

    public static final String REQUEST_DURATION = "lifesync.ai.request.duration";
    public static final String REQUESTS = "lifesync.ai.requests";
    public static final String TOKENS = "lifesync.ai.tokens";
    public static final String REJECTIONS = "lifesync.ai.rejections";

    /** Motivos de um pedido barrado antes de chegar ao provedor. */
    public enum Rejection { DISABLED, QUOTA_EXCEEDED }

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Registra uma chamada ao provedor que terminou, com sucesso ou nao. */
    public void recordCall(AiFeature feature, String provider, AiRequestStatus status, long latencyMs,
                           int inputTokens, int outputTokens) {
        String featureTag = feature.name();
        String providerTag = provider == null || provider.isBlank() ? "unknown" : provider;
        String statusTag = status.name();

        Timer.builder(REQUEST_DURATION)
                .description("Duracao da chamada ao provedor de LLM")
                .tags("feature", featureTag, "provider", providerTag, "status", statusTag)
                .register(registry)
                .record(Duration.ofMillis(Math.max(latencyMs, 0)));

        Counter.builder(REQUESTS)
                .description("Chamadas ao provedor de LLM por status")
                .tags("feature", featureTag, "provider", providerTag, "status", statusTag)
                .register(registry)
                .increment();

        addTokens(featureTag, providerTag, "input", inputTokens);
        addTokens(featureTag, providerTag, "output", outputTokens);
    }

    /** Registra um pedido barrado antes de chamar o provedor. */
    public void recordRejection(AiFeature feature, Rejection reason) {
        Counter.builder(REJECTIONS)
                .description("Pedidos barrados antes do provedor de LLM")
                .tags("feature", feature.name(), "reason", reason.name())
                .register(registry)
                .increment();
    }

    private void addTokens(String feature, String provider, String direction, int tokens) {
        if (tokens <= 0) {
            return; // erro do provedor nao informa tokens: nao inventa
        }
        Counter.builder(TOKENS)
                .description("Tokens consumidos pelo provedor de LLM")
                .baseUnit("tokens")
                .tags("feature", feature, "provider", provider, "direction", direction)
                .register(registry)
                .increment(tokens);
    }
}
