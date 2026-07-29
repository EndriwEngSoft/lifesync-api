package com.lifesync.api.task.enums;

/**
 * Transicoes entre esses status ainda nao sao validadas em codigo - hoje
 * qualquer mudanca de status e aceita. Validar transicoes (ex: impedir
 * voltar de DONE pra CANCELLED sem passar por IN_PROGRESS) exigiria
 * definir a maquina de estados primeiro, o que ainda nao foi feito.
 */
public enum Status {

    PENDING,
    IN_PROGRESS,
    DONE,
    CANCELLED

}
