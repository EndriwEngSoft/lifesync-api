package com.lifesync.api.task.enums;

/**
 * Transicoes entre esses status ainda nao sao validadas em codigo.
 * Ver ARCHITECTURE.md secao 7: InvalidStateTransitionException entra
 * quando o TaskService for implementado.
 */
public enum Status {

    PENDING,
    IN_PROGRESS,
    DONE,
    CANCELLED

}
