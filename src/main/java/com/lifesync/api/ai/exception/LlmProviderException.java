package com.lifesync.api.ai.exception;

/**
 * Falha ao falar com o provedor de LLM, classificada por tipo para o servico
 * decidir o status registrado no log e o handler decidir o HTTP. A mensagem
 * nunca carrega corpo da resposta do provedor (pode ecoar dado do usuario)
 * nem a chave.
 */
public class LlmProviderException extends RuntimeException {

    public enum Type {
        /** HTTP 429 do provedor (limite dele, nao a cota diaria do usuario). */
        RATE_LIMITED(true),
        /** HTTP 5xx ou provedor inalcancavel. */
        SERVER_ERROR(true),
        /** Estouro de timeout de conexao ou de leitura. */
        TIMEOUT(true),
        /** Outro 4xx: requisicao, chave ou modelo invalidos. Erro nosso, nao repete. */
        CLIENT_ERROR(false),
        /** Sem candidatos, sem conteudo ou texto vazio. */
        EMPTY_RESPONSE(false),
        /** Bloqueio de seguranca do prompt ou da resposta. */
        BLOCKED(false),
        /** Corpo malformado ou resposta cortada (MAX_TOKENS). */
        INVALID_RESPONSE(false);

        private final boolean transientError;

        Type(boolean transientError) {
            this.transientError = transientError;
        }

        /** Vale tentar de novo (erro provavelmente passageiro)? */
        public boolean isTransient() {
            return transientError;
        }
    }

    private final Type type;

    public LlmProviderException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public LlmProviderException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
