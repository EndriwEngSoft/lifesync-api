package com.lifesync.api.habit.enums;

/**
 * Define o que conta como "periodo" no calculo de streak: um dia, uma
 * semana ou um mes. WEEKLY e MONTHLY comparam o periodo inteiro, nao a
 * data exata - um check-in na sexta e outro na segunda seguinte ainda
 * contam como semanas consecutivas.
 */
public enum HabitFrequency {

    DAILY,
    WEEKLY,
    MONTHLY

}
