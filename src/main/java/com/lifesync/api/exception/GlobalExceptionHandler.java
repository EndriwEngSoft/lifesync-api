package com.lifesync.api.exception;

import com.lifesync.api.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Handler global de excecoes da API. Todo Service/Controller que lanca uma
 * exception de negocio cai aqui, e sai daqui como um ApiErrorResponse
 * padronizado - nunca como stack trace cru na resposta.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Falha de Bean Validation (@Valid em @RequestBody) - campo
     * obrigatorio faltando, formato invalido, etc. Sem esse handler
     * especifico, MethodArgumentNotValidException caia no catch-all de
     * Exception.class la embaixo e virava 500 - errado, porque e erro do
     * cliente (dado invalido), nao falha da aplicacao. Junta as mensagens
     * de todos os campos que falharam numa string so, separadas por
     * virgula - simples, mas cobre o caso comum (mais de um campo
     * invalido na mesma requisicao).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", errorMessage);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), errorMessage, request.getRequestURI()));
    }

    /**
     * Credenciais invalidas no login (email ou senha errados) -
     * AuthenticationManager.authenticate() lanca isso via
     * DaoAuthenticationProvider quando a senha nao bate. Mensagem de
     * resposta fixa ("Invalid credentials"), nao e.getMessage() -
     * mesmo raciocinio ja aplicado em UserDetailsServiceImpl: nao
     * revelar se foi o email que nao existe ou a senha que errou.
     * Sem esse handler, caia no catch-all de Exception.class e virava
     * 500 - mesma categoria de problema do handler de validacao acima.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException e, HttpServletRequest request) {
        log.warn("Authentication failed: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Invalid credentials",
                        request.getRequestURI()));
    }

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
     * Conta desativada tentando executar uma acao que exige estar ativa
     * (ex: renovar token). WARN, sem stack trace - causa ja conhecida.
     */
    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ApiErrorResponse> handleInactiveAccountException(
            InactiveAccountException e,  HttpServletRequest request) {
        log.warn("Inactive account exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(HttpStatus.FORBIDDEN.value(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * Token JWT invalido no contexto (assinatura/expiracao quebrada, ou
     * tipo errado - access no lugar de refresh, ou vice-versa). WARN,
     * sem stack trace - causa ja conhecida.
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTokenException(
            InvalidTokenException e,  HttpServletRequest request) {
        log.warn("Invalid token exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * Timezone de perfil invalido. O valor deve ser um ID IANA aceito pelo
     * java.time.ZoneId, como America/Sao_Paulo.
     */
    @ExceptionHandler(InvalidTimezoneException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTimezoneException(
            InvalidTimezoneException e, HttpServletRequest request) {
        log.warn("Invalid timezone exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * IDOR revelado (403 - o recurso existe, mas o acesso e negado).
     * Ainda sem uso real no codigo - ver Javadoc de UnauthorizedActionException.
     */
    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedActionException(
            UnauthorizedActionException e,  HttpServletRequest request) {
        log.warn("Unauthorized action exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(HttpStatus.FORBIDDEN.value(), e.getMessage(), request.getRequestURI()));
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
