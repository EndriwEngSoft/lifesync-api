package com.lifesync.api.exception;

import com.lifesync.api.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler global de excecoes da API. Todo Service/Controller que lanca uma
 * exception de negocio cai aqui, e sai daqui como um ApiErrorResponse
 * padronizado — nunca como stack trace cru na resposta.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Email/username duplicado no cadastro. WARN e suficiente aqui,
     * ja sabemos exatamente a causa — nao precisa de stack trace.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException e,  HttpServletRequest request) {
        log.warn("Duplicate resource exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * Recurso buscado por id (ou outro identificador) que nao existe.
     * Mesmo raciocinio do handler acima: WARN, sem stack trace.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException e,  HttpServletRequest request) {
        log.warn("Resource not found exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * Catch-all pra qualquer exception que nao foi prevista pelos handlers
     * acima. A resposta pro cliente fica com mensagem generica de proposito
     * — o motivo real vai so pro log (ERROR, com stack trace completo),
     * pra nao vazar detalhe interno (nome de tabela, query, etc) pra fora.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(
            Exception e,  HttpServletRequest request) {
        log.error("Unexpected error occurred" , e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error",
                        request.getRequestURI()));
    }
}
