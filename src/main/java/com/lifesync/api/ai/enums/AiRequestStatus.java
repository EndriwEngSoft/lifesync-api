package com.lifesync.api.ai.enums;

import java.util.Set;

/** Resultado de uma chamada ao LLM, como registrado no log. */
public enum AiRequestStatus {
    /** Resposta valida, plano devolvido ao usuario. */
    SUCCESS,
    /** O provedor falhou (429, 5xx, bloqueio, resposta vazia...). */
    PROVIDER_ERROR,
    /** Passo intermediario do agente: o modelo pediu ferramentas e a conversa continuou. Nao consome cota. */
    TOOL_STEP,
    /** O provedor respondeu, mas a saida nao passou na validacao. */
    INVALID_OUTPUT,
    /** Estouro de timeout. */
    TIMEOUT;

    /**
     * Status que consomem a cota diaria: chamadas que chegaram a gerar
     * resposta (e custo). Falha do provedor ou timeout nao tiram cota do
     * usuario - o problema nao foi dele.
     */
    public static final Set<AiRequestStatus> COUNTED_FOR_QUOTA = Set.of(SUCCESS, INVALID_OUTPUT);
}
