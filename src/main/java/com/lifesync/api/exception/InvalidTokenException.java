package com.lifesync.api.exception;

/**
 * Lancada quando um token JWT apresentado e invalido para o contexto:
 * assinatura/expiracao invalida, ou tipo errado (ex: access token usado
 * onde se espera um refresh token, ou vice-versa). Traduzida pelo
 * GlobalExceptionHandler para HTTP 401 Unauthorized.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

}
