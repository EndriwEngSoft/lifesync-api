package com.lifesync.api.ai.client;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
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