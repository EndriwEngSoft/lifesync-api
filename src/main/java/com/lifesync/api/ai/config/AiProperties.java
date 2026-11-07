package com.lifesync.api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Locale;

/**
 * Configuracao do modulo de IA (prefixo lifesync.ai). Valores padrao seguros
 * e validacao na construcao: configuracao incoerente derruba a aplicacao na
 * subida, em vez de aparecer so na primeira chamada de um usuario.
 *
 * A chave do provedor nunca aparece em toString nem em log.
 *
 * @param enabled        liga o modulo; desligado, o endpoint responde 503
 * @param provider       "fake" ou "gemini"; vazio = nenhum cliente registrado
 * @param apiKey         chave do provedor (vem de LLM_API_KEY, nunca do repositorio)
 * @param connectTimeout tempo maximo para abrir a conexao
 * @param readTimeout    tempo maximo de espera da resposta
 * @param maxTokens      teto de tokens de saida por chamada
 * @param dailyQuota     chamadas bem-sucedidas por usuario por dia
 * @param maxAttempts    tentativas totais por chamada (1 = sem retry)
 * @param retryBackoff   espera base entre tentativas (dobra a cada tentativa)
 */
@ConfigurationProperties(prefix = "lifesync.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String provider,
        @DefaultValue("") String apiKey,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("gemini-3.5-flash-lite") String model,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("5") int dailyQuota,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("500ms") Duration retryBackoff
) {

    public static final String PROVIDER_FAKE = "fake";
    public static final String PROVIDER_GEMINI = "gemini";

    public AiProperties {
        provider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        apiKey = apiKey == null ? "" : apiKey.trim();

        requirePositive(connectTimeout, "lifesync.ai.connect-timeout");
        requirePositive(readTimeout, "lifesync.ai.read-timeout");
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("lifesync.ai.retry-backoff must not be negative");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("lifesync.ai.max-tokens must be positive");
        }
        if (dailyQuota < 1) {
            throw new IllegalArgumentException("lifesync.ai.daily-quota must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("lifesync.ai.max-attempts must be between 1 and 5");
        }

        if (enabled) {
            if (!PROVIDER_FAKE.equals(provider) && !PROVIDER_GEMINI.equals(provider)) {
                throw new IllegalArgumentException(
                        "lifesync.ai.provider must be 'fake' or 'gemini' when lifesync.ai.enabled=true");
            }
            if (PROVIDER_GEMINI.equals(provider)) {
                if (apiKey.isEmpty()) {
                    throw new IllegalArgumentException(
                            "lifesync.ai.api-key (LLM_API_KEY) is required when lifesync.ai.enabled=true"
                                    + " and lifesync.ai.provider=gemini");
                }
                if (baseUrl == null || baseUrl.isBlank()) {
                    throw new IllegalArgumentException("lifesync.ai.base-url must not be blank");
                }
                if (model == null || model.isBlank()) {
                    throw new IllegalArgumentException("lifesync.ai.model must not be blank");
                }
            }
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /** Sem a chave: o toString de um record imprimiria todos os componentes. */
    @Override
    public String toString() {
        return "AiProperties[enabled=" + enabled
                + ", provider=" + provider
                + ", apiKey=" + (apiKey.isEmpty() ? "<empty>" : "<masked>")
                + ", baseUrl=" + baseUrl
                + ", model=" + model
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", maxTokens=" + maxTokens
                + ", dailyQuota=" + dailyQuota
                + ", maxAttempts=" + maxAttempts
                + ", retryBackoff=" + retryBackoff + "]";
    }
}
