package com.lifesync.api.common;

import lombok.Getter;

import java.time.Instant;

/**
 * Formato padronizado de resposta para qualquer erro da API, montado pelo
 * GlobalExceptionHandler. Classe simples (sem builder): e sempre construida
 * da mesma forma, em um unico ponto de uso, entao builder so adicionaria
 * cerimonia sem ganho real aqui.
 *
 * Atencao: "message" e "path" sao dois parametros String adjacentes no
 * construtor — risco de inversao sem erro de compilacao. Aceito
 * conscientemente pelo baixo risco pratico neste caso.
 */
@Getter
public class ApiErrorResponse {

    private int status;
    private String message;
    private Instant timestamp;
    private String path;

    public ApiErrorResponse(int status, String message, String path) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
        this.path = path;
    }
}
