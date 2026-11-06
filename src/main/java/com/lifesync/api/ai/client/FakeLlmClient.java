package com.lifesync.api.ai.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cliente de mentira, so ativo com lifesync.ai.provider=fake. Sem @Primary e sem
 * condicao aberta: em producao ele nao existe, entao nunca responde no lugar do
 * provedor real.
 */
@Component
@ConditionalOnProperty(prefix = "lifesync.ai", name = "provider", havingValue = "fake")
public class FakeLlmClient implements LlmClient {

    @Override
    public LlmResponse generatePlan(LlmRequest request) {
        // Retorna um JSON simulado e estruturado exatamente como o esperado para testes
        String simulatedJsonResponse = """
                {
                  "summary": "Plano semanal gerado pelo modo de teste (Fake).",
                  "priorities": [
                    {
                      "type": "TASK",
                      "referenceId": "T1",
                      "reason": "Prioridade alta simulada para testes."
                    }
                  ],
                  "risks": [
                    "Nenhum risco detectado no ambiente simulado."
                  ],
                  "schedule": {
                    "MONDAY": [
                      "Focar na tarefa T1."
                    ]
                  }
                }
                """;

        return new LlmResponse(
                simulatedJsonResponse,
                150, // tokens de entrada simulados
                100, // tokens de saída simulados
                "gemini-fake-model",
                45   // latência simulada em ms
        );
    }
}