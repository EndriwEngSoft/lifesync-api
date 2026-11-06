package com.lifesync.api.ai.client;

import com.lifesync.api.ai.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClient;

import java.util.List;

/** So ativo com lifesync.ai.provider=gemini. */
@Service
@ConditionalOnProperty(prefix = "lifesync.ai", name = "provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final AiProperties properties;

    public GeminiLlmClient(AiProperties properties, RestClient geminiRestClient) {
        this.properties = properties;
        this.restClient = geminiRestClient;
    }

    @Override
    public LlmResponse generatePlan(LlmRequest request) {
        String fullPrompt = request.systemPrompt() + "\n\n<user_data>\n" + request.userContextJson() + "\n</user_data>";
        GeminiRequest geminiReq = new GeminiRequest(List.of(
                new GeminiContent("user", List.of(new GeminiPart(fullPrompt)))
        ));

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        GeminiResponse response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", properties.model())
                .body(geminiReq)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 429) {
                        throw new RuntimeException("Cota do provedor LLM excedida (HTTP 429).");
                    }
                    throw new RuntimeException("Erro na requisição ao LLM: " + res.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new RuntimeException("O provedor LLM está indisponível (HTTP " + res.getStatusCode() + ").");
                })
                .body(GeminiResponse.class);

        stopWatch.stop();

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new RuntimeException("Resposta vazia ou inválida do provedor LLM.");
        }

        String textContent = response.candidates().getFirst().content().parts().getFirst().text();
        int inTokens = response.usageMetadata() != null ? response.usageMetadata().promptTokenCount() : 0;
        int outTokens = response.usageMetadata() != null ? response.usageMetadata().candidatesTokenCount() : 0;

        return new LlmResponse(textContent, inTokens, outTokens, properties.model(), stopWatch.getTotalTimeMillis());
    }

    // --- DTOs Privados Específicos da API do Google Gemini ---

    private record GeminiPart(String text) {}
    private record GeminiContent(String role, List<GeminiPart> parts) {}
    private record GeminiRequest(List<GeminiContent> contents) {}

    private record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {
        public record Candidate(GeminiContent content) {}
        public record UsageMetadata(int promptTokenCount, int candidatesTokenCount) {}
    }
}