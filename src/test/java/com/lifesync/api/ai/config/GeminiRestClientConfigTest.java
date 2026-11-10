package com.lifesync.api.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O timeout de leitura realmente vale: um servidor local aceita a conexao e
 * nunca responde. Nenhum provedor real e contatado.
 */
class GeminiRestClientConfigTest {

    @Test
    void readTimeout_AbortsARequestThatNeverGetsAnAnswer() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            AiProperties props = new AiProperties(true, "gemini", "chave-de-teste",
                    "http://localhost:" + silent.getLocalPort(), "m",
                    Duration.ofSeconds(2), Duration.ofMillis(300), 100, 5, 1, Duration.ZERO, List.of("*"));
            RestClient client = new GeminiRestClientConfig().geminiRestClient(props);

            long start = System.nanoTime();
            assertThatThrownBy(() -> client.post().uri("/x").body("{}").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(elapsedMs).isLessThan(5_000);
        }
    }
}
