package com.lifesync.api.ai.exception;

/** O usuario ja usou a cota diaria do recurso de IA. Vira 429. */
public class AiQuotaExceededException extends RuntimeException {

    public AiQuotaExceededException(String message) {
        super(message);
    }
}
