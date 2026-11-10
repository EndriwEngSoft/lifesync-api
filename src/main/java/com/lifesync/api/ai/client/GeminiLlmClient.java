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
import tools.jackson.databind.JsonNode;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * - resposta sem candidatos, vazia ou bloqueada vira LlmProviderException
 *   tipada, nunca NullPointerException; a cortada no teto de tokens e'
 *   devolvida com os tokens gastos, para o servico rejeitar e contabilizar;
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

    /** Motivos de parada em que o modelo nao conseguiu montar um pedido de ferramenta valido. */
    private static final Set<String> TOOL_FAILURE_FINISH_REASONS = Set.of(
            "MALFORMED_FUNCTION_CALL", "UNEXPECTED_TOOL_CALL", "TOO_MANY_TOOL_CALLS",
            "MISSING_THOUGHT_SIGNATURE", "MALFORMED_RESPONSE");

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
        GeminiResponse response = callWithRetry(body, GeminiResponse.class);
        stopWatch.stop();

        return toLlmResponse(response, stopWatch.getTotalTimeMillis());
    }

    // --- Chamada com ferramentas (function calling) ---

    /**
     * Um passo da conversa com ferramentas. Formato conferido na referencia do
     * generateContent e por execucao (ver docs/ai/adr-0003-agente-ferramentas.md):
     * tools[].functionDeclarations[].{name,description,parametersJsonSchema};
     * o modelo responde com parts[].functionCall.{id,name,args}; o resultado
     * volta num turno "user" com parts[].functionResponse.{id,name,response},
     * depois do turno do modelo devolvido sem alteracao.
     */
    @Override
    public LlmToolResponse chat(LlmToolRequest request) {
        Map<String, Object> body = buildToolRequest(request);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        JsonNode response = callWithRetry(body, JsonNode.class);
        stopWatch.stop();

        return toToolResponse(response, stopWatch.getTotalTimeMillis());
    }

    private Map<String, Object> buildToolRequest(LlmToolRequest request) {
        List<Object> contents = new ArrayList<>();
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", request.userMessage()))));
        for (ToolExchange exchange : request.history()) {
            contents.add(exchange.rawModelTurn()); // intacto: carrega a assinatura de raciocinio
            List<Object> parts = new ArrayList<>();
            for (ToolResult result : exchange.results()) {
                Map<String, Object> functionResponse = new LinkedHashMap<>();
                if (result.callId() != null) {
                    functionResponse.put("id", result.callId());
                }
                functionResponse.put("name", result.name());
                functionResponse.put("response", result.response());
                parts.add(Map.of("functionResponse", functionResponse));
            }
            contents.add(Map.of("role", "user", "parts", parts));
        }

        List<Object> declarations = new ArrayList<>();
        for (ToolSpec tool : request.tools()) {
            Map<String, Object> declaration = new LinkedHashMap<>();
            declaration.put("name", tool.name());
            declaration.put("description", tool.description());
            declaration.put("parametersJsonSchema", tool.parametersSchema());
            declarations.add(declaration);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemPrompt()))));
        body.put("contents", contents);
        body.put("tools", List.of(Map.of("functionDeclarations", declarations)));
        body.put("generationConfig", Map.of("maxOutputTokens", properties.maxTokens()));
        return body;
    }

    private LlmToolResponse toToolResponse(JsonNode response, long latencyMs) {
        if (isRealBlock(response.path("promptFeedback").path("blockReason").stringValue(null))) {
            throw new LlmProviderException(Type.BLOCKED, "Prompt was blocked by the provider.");
        }
        JsonNode candidate = response.path("candidates").path(0);
        if (!candidate.isObject()) {
            throw new LlmProviderException(Type.EMPTY_RESPONSE, "Provider returned no candidates.");
        }
        String finishReason = candidate.path("finishReason").stringValue(null);
        if (finishReason != null && BLOCKING_FINISH_REASONS.contains(finishReason)) {
            throw new LlmProviderException(Type.BLOCKED, "Response was blocked by the provider.");
        }
        if (finishReason != null && TOOL_FAILURE_FINISH_REASONS.contains(finishReason)) {
            throw new LlmProviderException(Type.INVALID_RESPONSE, "Provider could not produce a valid tool call.");
        }

        JsonNode content = candidate.path("content");
        List<ToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            JsonNode functionCall = part.path("functionCall");
            if (functionCall.isObject()) {
                calls.add(toToolCall(functionCall));
            } else if (part.path("text").isString() && !part.path("thought").booleanValue(false)) {
                text.append(part.path("text").stringValue());
            }
        }

        boolean truncated = "MAX_TOKENS".equals(finishReason);
        if (calls.isEmpty() && text.toString().isBlank() && !truncated) {
            throw new LlmProviderException(Type.EMPTY_RESPONSE, "Provider returned no text.");
        }

        JsonNode usage = response.path("usageMetadata");
        int inputTokens = usage.path("promptTokenCount").asInt(0);
        int outputTokens = usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0);
        return new LlmToolResponse(text.toString(), List.copyOf(calls), content, truncated,
                inputTokens, outputTokens, properties.model(), latencyMs);
    }

    private static ToolCall toToolCall(JsonNode functionCall) {
        String name = functionCall.path("name").stringValue(null);
        JsonNode args = functionCall.path("args");
        if (name == null || name.isBlank() || !(args.isObject() || args.isMissingNode() || args.isNull())) {
            throw new LlmProviderException(Type.INVALID_RESPONSE, "Provider returned a malformed tool call.");
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (args.isObject()) {
            args.properties().forEach(e -> arguments.put(e.getKey(), toJava(e.getValue())));
        }
        return new ToolCall(functionCall.path("id").stringValue(null), name, arguments);
    }

    /** Converte um no JSON em tipos simples do Java, para o servico validar os argumentos. */
    private static Object toJava(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.properties().forEach(e -> map.put(e.getKey(), toJava(e.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(toJava(item)));
            return list;
        }
        if (node.isString()) {
            return node.stringValue();
        }
        if (node.isIntegralNumber()) {
            // Inteiro que nao cabe em long vira BigInteger: a validacao da ferramenta o rejeita com erro
            // tipado, em vez de a conversao estourar aqui.
            return node.canConvertToLong() ? (Object) node.longValue() : node.bigIntegerValue();
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return null;
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
     * Troca '<' e '>' pelas sequencias de escape JSON (barra invertida + u003c
     * e barra invertida + u003e). Como o contexto e'
     * JSON e esses caracteres so aparecem dentro de strings, a troca mantem o
     * JSON valido e impede que um titulo contendo "</user_data>" feche a tag.
     */
    static String escapeDelimiters(String json) {
        return json == null ? "" : json.replace("<", "\\u003c").replace(">", "\\u003e");
    }

    // --- Chamada com retry ---

    private <T> T callWithRetry(Object body, Class<T> responseType) {
        int maxAttempts = properties.maxAttempts();
        LlmProviderException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call(body, responseType);
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

    private <T> T call(Object body, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .body(body)
                    .retrieve()
                    .body(responseType);
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

        // Resposta cortada no teto de tokens foi gerada e cobrada: ela segue adiante, mesmo vazia,
        // com os tokens reais. A validacao do servico a rejeita como saida invalida, que consome
        // cota; se virasse erro do provedor, repetir a chamada mais cara sairia de graca.
        boolean cutAtTokenLimit = "MAX_TOKENS".equals(finishReason);
        String text = extractText(candidate);
        if (text.isBlank() && !cutAtTokenLimit) {
            throw new LlmProviderException(Type.EMPTY_RESPONSE, "Provider returned no text.");
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
