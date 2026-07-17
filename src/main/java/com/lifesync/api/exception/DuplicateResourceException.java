package com.lifesync.api.exception;

/**
 * Lancada quando uma regra de unicidade de negocio e violada
 * (ex: cadastro com email ja existente). Traduzida pelo
 * GlobalExceptionHandler para HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

}
