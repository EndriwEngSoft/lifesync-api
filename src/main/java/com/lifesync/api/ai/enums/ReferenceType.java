package com.lifesync.api.ai.enums;

/** Tipo de item que o plano pode referenciar, com a letra do indice curto (T1, H1, G1). */
public enum ReferenceType {
    TASK("T"),
    HABIT("H"),
    GOAL("G");

    private final String prefix;

    ReferenceType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public String indexFor(int position) {
        return prefix + position;
    }
}
