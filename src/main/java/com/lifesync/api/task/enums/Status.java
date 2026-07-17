package com.lifesync.api.task.enums;

// Transicoes validas ainda nao sao validadas em codigo (ver secao 7 do
// ARCHITECTURE.md: InvalidStateTransitionException, a criar quando o
// TaskService de fato existir).
public enum Status {

    PENDING,
    IN_PROGRESS,
    DONE,
    CANCELLED

}
