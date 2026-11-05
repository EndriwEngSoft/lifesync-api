package com.lifesync.api.ai.client;

import com.lifesync.api.ai.config.AiProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiLlmClient implements LlmClient {

    private final RestClient restClient;

    public GeminiLlmClient(AiProperties properties, RestClient.Builder builder) {
        // Busca a chave de API diretamente das variáveis de ambiente do SO
        String apiKey = System.getenv("LLM_API_KEY");

        // Configura a fábrica de requisições nativa do JDK para aplicar os timeouts
        var factory = new JdkClientHttpRequestFactory();
        if (properties.timeout() != null) {
            factory.setReadTimeout(properties.timeout());
        }

        // Constrói o RestClient uma única vez com a URL base e o cabeçalho de autenticação do Gemini
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(factory)
                .build();
    }

    @Override
    public LlmResponse generatePlan(LlmRequest request) {
        return null;
    }
}