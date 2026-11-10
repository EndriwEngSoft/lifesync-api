package com.lifesync.api.ai.client;

public interface LlmClient {

    /** Uma chamada simples: contexto do usuario -> plano em JSON. */
    LlmResponse generatePlan(LlmRequest request);

    /**
     * Um passo de uma conversa com ferramentas: o modelo responde em texto ou
     * pede a execucao de ferramentas. Quem executa as ferramentas e controla o
     * loop e' o servico, nunca o cliente.
     */
    LlmToolResponse chat(LlmToolRequest request);
}
