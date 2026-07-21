package com.lifesync.api.exception;

/**
 * Lancada quando uma acao e tentada por uma conta desativada (active =
 * false). Diferente de UnauthorizedActionException (IDOR - usuario nao e
 * dono do recurso): aqui o usuario e dono de si mesmo, so que a conta esta
 * num estado que nao permite a acao. Traduzida pelo GlobalExceptionHandler
 * para HTTP 403 Forbidden.
 */
public class InactiveAccountException extends RuntimeException {

    public InactiveAccountException(String message) {
        super(message);
    }

}
