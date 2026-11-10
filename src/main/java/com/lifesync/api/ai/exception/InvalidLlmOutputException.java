package com.lifesync.api.ai.exception;

/**
 * O provedor respondeu, mas a saida nao serve: JSON invalido, campos fora das
 * regras ou indice de item que nao existe. Vira 502 (a resposta do servico
 * upstream e' que esta errada). A mensagem nunca traz o texto do modelo.
 */
public class InvalidLlmOutputException extends RuntimeException {

    public InvalidLlmOutputException(String message) {
        super(message);
    }

    public InvalidLlmOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
