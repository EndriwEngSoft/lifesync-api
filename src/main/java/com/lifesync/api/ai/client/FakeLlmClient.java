package com.lifesync.api.ai.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente de mentira, so ativo com lifesync.ai.provider=fake. Sem @Primary e sem
 * condicao aberta: em producao ele nao existe, entao nunca responde no lugar do
 * provedor real. Nao faz chamada de rede.
 *
 * Devolve um plano valido que referencia o primeiro item de cada tipo que
 * existir no contexto recebido (T, H e G), para o fluxo completo funcionar em
 * dev e nos testes sem inventar indices.
 */
@Component
@ConditionalOnProperty(prefix = "lifesync.ai", name = "provider", havingValue = "fake")
public class FakeLlmClient implements LlmClient {

    private static final Pattern INDEX = Pattern.compile("\"shortIndex\"\\s*:\\s*\"([THG][0-9]+)\"");

    @Override
    public LlmResponse generatePlan(LlmRequest request) {
        List<String> priorities = new ArrayList<>();
        addFirstOf("T", "TASK", request.userContextJson(), priorities);
        addFirstOf("H", "HABIT", request.userContextJson(), priorities);
        addFirstOf("G", "GOAL", request.userContextJson(), priorities);

        String json = """
                {
                  "summary": "Plano semanal gerado pelo modo de teste (Fake).",
                  "priorities": [%s],
                  "risks": ["Nenhum risco detectado no ambiente simulado."],
                  "schedule": {
                    "MONDAY": ["Começar pelos itens prioritários."],
                    "FRIDAY": ["Revisar o que foi concluído na semana."]
                  }
                }
                """.formatted(String.join(", ", priorities));

        return new LlmResponse(json, 150, 100, "fake-model", 1);
    }

    /**
     * Conversa simulada em dois passos: primeiro pede a ferramenta
     * tarefas_atrasadas; com o resultado em maos, responde em texto.
     */
    @Override
    public LlmToolResponse chat(LlmToolRequest request) {
        if (request.history().isEmpty()) {
            ToolCall call = new ToolCall("fake-call-1", "tarefas_atrasadas", Map.of());
            Map<String, Object> rawTurn = Map.of("role", "model", "parts", List.of(
                    Map.of("functionCall", Map.of("id", call.id(), "name", call.name(), "args", Map.of()))));
            return new LlmToolResponse("", List.of(call), rawTurn, false, 120, 10, "fake-model", 1);
        }
        return new LlmToolResponse("Resposta do modo de teste (Fake): consultei suas tarefas atrasadas.",
                List.of(), null, false, 160, 20, "fake-model", 1);
    }

    private static void addFirstOf(String prefix, String type, String contextJson, List<String> out) {
        if (contextJson == null) {
            return;
        }
        Matcher matcher = INDEX.matcher(contextJson);
        while (matcher.find()) {
            if (matcher.group(1).startsWith(prefix)) {
                out.add("{\"type\": \"%s\", \"referenceId\": \"%s\", \"reason\": \"Prioridade simulada.\"}"
                        .formatted(type, matcher.group(1)));
                return;
            }
        }
    }
}
