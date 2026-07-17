package com.lifesync.api.exception;

/**
 * Lancada quando um recurso buscado por id (ou outro identificador)
 * nao existe. Traduzida pelo GlobalExceptionHandler para HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

}
