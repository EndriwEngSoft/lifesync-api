package com.lifesync.api.ai.exception;

/**
 * O modulo de IA esta desligado (lifesync.ai.enabled=false) ou nao ha
 * provedor configurado. Vira 503: o recurso existe, mas esta indisponivel
 * por configuracao - nao e erro do cliente nem falha do provedor.
 */
public class AiDisabledException extends RuntimeException {

    public AiDisabledException(String message) {
        super(message);
    }
}
