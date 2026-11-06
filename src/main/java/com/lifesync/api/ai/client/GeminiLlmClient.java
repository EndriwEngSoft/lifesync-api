package com.lifesync.api.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.exception.LlmProviderException.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adaptador do Gemini (generateContent) sobre RestClient. Campos conferidos na
 * documentacao oficial do Gemini API: systemInstruction, contents,
 * generationConfig.{maxOutputTokens,responseMimeType,responseJsonSchema},
 * candidates[].{content.parts[].text,finishReason}, promptFeedback.blockReason
 * e usageMetadata.
 *
 * Regras deste adaptador:
 * - as instrucoes fixas vao em systemInstruction; os dados do usuario, em
 *   contents, entre <user_data>, com '<' e '>' escapados para o usuario nao
 *   conseguir fechar a tag;
 * - resposta sem candidatos, bloqueada ou cortada vira LlmProviderException
 *   tipada, nunca NullPointerException;
 * - so erros passageiros (429, 5xx, timeout) sao repetidos, com backoff
 *   exponencial e numero maximo de tentativas;
 * - nem a chave nem o corpo das respostas aparecem em mensagem ou log.
 *
 * So ativo com lifesync.ai.provider=gemini.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lifesync.ai", name = "provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private static final Set<String> BLOCKING_FINISH_REASONS = Set.of(
            "SAFETY", "RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION");

    private final RestClient restClient;
    private final AiProperties properties;

    public GeminiLlmClient(AiProperties properties, RestClient geminiRestClient) {
        this.properties = properties;
        this.restClient = geminiRestClient;
    }

    @Override
    public LlmResponse generatePlan(LlmRequest request) {
        GeminiRequest body = buildRequest(request);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        GeminiResponse response = callWithRetry(body);
        stopWatch.stop();

        return toLlmResponse(response, stopWatch.getTotalTimeMillis());
    }

    // --- Montagem da requisicao ---

    private GeminiRequest buildRequest(LlmRequest request) {
        String userText = "<user_data>\n" + escapeDelimiters(request.userContextJson()) + "\n</user_data>";
        return new GeminiRequest(
                new GeminiContent(null, List.of(new GeminiPart(request.systemPrompt()))),
                List.of(new GeminiContent("user", List.of(new GeminiPart(userText)))),
                new GenerationConfig(properties.maxTokens(), "application/json", request.responseSchema()));
    }

    /**
     * Troca '<' e '>' pelas sequencias JSON < e >. Como o contexto e'
     * JSON e esses caracteres so aparecem dentro de strings, a troca mantem o
     * JSON valido e impede que um titulo contendo "</user_data>" feche a tag.
     */
    static String escapeDelimiters(String json) {
        return json == null ? "" : json.replace("<", "\\u003c").replace(">", "\\u003e");
    }

    // --- Chamada com retry ---

    private GeminiResponse callWithRetry(GeminiRequest body) {
        int maxAttempts = properties.maxAttempts();
        LlmProviderException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call(body);
            } catch (LlmProviderException e) {
                last = e;
                if (!e.getType().isTransient() || attempt == maxAttempts) {
                    throw e;
                }
                log.warn("LLM call failed ({}), attempt {}/{}", e.getType(), attempt, maxAttempts);
                if (!backoff(attempt)) {
                    throw e;
                }
            }
        }
        throw last; // inalcancavel: maxAttempts >= 1 garante retorno ou excecao acima
    }

    /** Espera retryBackoff * 2^(tentativa-1). Devolve false se a thread foi interrompida. */
    private boolean backoff(int attempt) {
        Duration wait = properties.retryBackoff().multipliedBy(1L << (attempt - 1));
        if (wait.isZero()) {
            return true;
        }
        try {
            Thread.sleep(wait.toMillis());
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private GeminiResponse call(GeminiRequest body) {
        try {
            GeminiResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            if (response == null) {
                throw new LlmProviderException(Type.EMPTY_RESPONSE, "Provider returned an empty body.");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw fromStatus(e);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new LlmProviderException(Type.TIMEOUT, "Provider call timed out.", e);
            }
            throw new LlmProviderException(Type.SERVER_ERROR, "Provider is unreachable.", e);
        } catch (RestClientException e) {
            throw new LlmProviderException(Type.INVALID_RESPONSE, "Provider response could not be read.", e);
        }
    }

    private LlmProviderException fromStatus(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 429) {
            return new LlmProviderException(Type.RATE_LIMITED, "Provider rate limit reached (HTTP 429).");
        }
        if (status >= 500) {
            return new LlmProviderException(Type.SERVER_ERROR, "Provider error (HTTP " + status + ").");
        }
        return new LlmProviderException(Type.CLIENT_ERROR, "Provider rejected the request (HTTP " + status + ").");
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException
                    || t instanceof InterruptedIOException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    // --- Leitura da resposta ---

    private LlmResponse toLlmResponse(GeminiResponse response, long latencyMs) {
        if (response.promptFeedback() != null && isRealBlock(response.promptFeedback().blockReason())) {
            throw new LlmProviderException(Type.BLOCKED, "Prompt was blocked by the provider.");
        }
        if (response.candidates() == null || response.candidates().isEmpty()
                || response.candidates().getFirst() == null) {
            throw new LlmProviderException(Type.EMPTY_RESPONSE, "Provider returned no candidates.");
        }

        Candidate candidate = response.candidates().getFirst();
        String finishReason = candidate.finishReason();
        if (finishReason != null && BLOCKING_FINISH_REASONS.contains(finishReason)) {
            throw new LlmProviderException(Type.BLOCKED, "Response was blocked by the provider.");
        }

        String text = extractText(candidate);
        if (text.isBlank()) {
            throw new LlmProviderException(Type.EMPTY_RESPONSE, "Provider returned no text.");
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new LlmProviderException(Type.INVALID_RESPONSE, "Response was cut at the token limit.");
        }

        UsageMetadata usage = response.usageMetadata();
        int inputTokens = usage == null ? 0 : orZero(usage.promptTokenCount());
        int outputTokens = usage == null ? 0 : orZero(usage.candidatesTokenCount()) + orZero(usage.thoughtsTokenCount());

        return new LlmResponse(text, inputTokens, outputTokens, properties.model(), latencyMs);
    }

    private static boolean isRealBlock(String blockReason) {
        return blockReason != null && !blockReason.isBlank() && !"BLOCK_REASON_UNSPECIFIED".equals(blockReason);
    }

    private static String extractText(Candidate candidate) {
        if (candidate.content() == null || candidate.content().parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (GeminiPart part : candidate.content().parts()) {
            if (part != null && part.text() != null) {
                sb.append(part.text());
            }
        }
        return sb.toString();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    // --- DTOs privados da API do Gemini ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GeminiPart(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GeminiContent(String role, List<GeminiPart> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerationConfig(int maxOutputTokens, String responseMimeType,
                                    Map<String, Object> responseJsonSchema) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GeminiRequest(GeminiContent systemInstruction, List<GeminiContent> contents,
                                 GenerationConfig generationConfig) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(GeminiContent content, String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PromptFeedback(String blockReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer thoughtsTokenCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback,
                                  UsageMetadata usageMetadata) {}
}
