package com.lifesync.api.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Monta o RestClient do Gemini com timeouts de conexao e de leitura. A chave
 * vai no header x-goog-api-key (nao na URL, para nao cair em log de acesso).
 * So existe com provider=gemini.
 */
@Configuration
@ConditionalOnProperty(prefix = "lifesync.ai", name = "provider", havingValue = "gemini")
public class GeminiRestClientConfig {

    public static final String BEAN_NAME = "geminiRestClient";

    @Bean(BEAN_NAME)
    public RestClient geminiRestClient(AiProperties properties) {
        return builder(properties).build();
    }

    /** Builder ja configurado; exposto para os testes ligarem um servidor de mentira. */
    public static RestClient.Builder builder(AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .requestFactory(factory);
    }
}
