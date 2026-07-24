package com.lifesync.api.exception;

/**
 * Reservada para o caso de IDOR onde a resposta deveria ser 403 (revelar
 * que o recurso existe, so negar acesso) em vez de 404 (esconder a
 * existencia). Ainda sem uso real: todo o modulo task/ hoje escolhe a
 * estrategia de 404 (findByIdAndUserId + ResourceNotFoundException),
 * entao essa exception fica pronta ate aparecer um caso concreto que
 * precise da resposta 403 explicita.
 */
public class UnauthorizedActionException extends RuntimeException{

    public UnauthorizedActionException(String message) {
        super(message);
    }

}
