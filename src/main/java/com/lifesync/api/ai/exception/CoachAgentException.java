package com.lifesync.api.ai.exception;

/**
 * O agente nao conseguiu concluir a conversa de forma segura. O tipo diz o
 * motivo; a mensagem nunca carrega texto do modelo nem dados do usuario.
 */
public class CoachAgentException extends RuntimeException {

    public enum Type {
        /** O modelo pediu uma ferramenta que nao existe. */
        UNKNOWN_TOOL,
        /** O modelo passou argumento desconhecido, de tipo errado ou fora do intervalo. */
        INVALID_TOOL_ARGUMENTS,
        /** O modelo pediu ferramentas demais num unico passo. */
        TOO_MANY_TOOL_CALLS,
        /** O numero maximo de passos acabou sem uma resposta final. */
        STEP_LIMIT,
        /** O tempo total da conversa acabou. */
        TIMEOUT,
        /** A resposta final veio vazia, cortada ou longa demais. */
        INVALID_ANSWER
    }

    private final Type type;

    public CoachAgentException(Type type) {
        super("Coach agent stopped: " + type);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
