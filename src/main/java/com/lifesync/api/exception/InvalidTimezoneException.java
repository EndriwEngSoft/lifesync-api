package com.lifesync.api.exception;

/**
 * Lançada quando o timezone informado pelo usuario nao e um ID IANA valido.
 * Traduzida para HTTP 400 pelo GlobalExceptionHandler.
 */
public class InvalidTimezoneException extends RuntimeException {

    public InvalidTimezoneException(String message) {
        super(message);
    }
}
