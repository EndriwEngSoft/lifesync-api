package com.lifesync.api.goal.entity;

import com.lifesync.api.goal.enums.GoalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para a entidade Goal (sem mocks).
 * Foco no método getProgressPercentage() — cálculo de progresso para o frontend.
 */
class GoalEntityTest {

    @Test
    void getProgressPercentage_WithHalfProgress_Returns50() {
        Goal goal = Goal.builder()
                .currentValue(BigDecimal.valueOf(500))
                .targetValue(BigDecimal.valueOf(1000))
                .unit("R$")
                .status(GoalStatus.IN_PROGRESS)
                .build();

        BigDecimal percentage = goal.getProgressPercentage();

        assertEquals(0, percentage.compareTo(BigDecimal.valueOf(50.0)),
                "500/1000 deve ser 50%");
    }

    @Test
    void getProgressPercentage_WithZeroProgress_Returns0() {
        Goal goal = Goal.builder()
                .currentValue(BigDecimal.ZERO)
                .targetValue(BigDecimal.valueOf(1000))
                .unit("R$")
                .status(GoalStatus.IN_PROGRESS)
                .build();

        BigDecimal percentage = goal.getProgressPercentage();

        assertEquals(0, percentage.compareTo(BigDecimal.ZERO),
                "Progresso zero deve ser 0%");
    }

    @Test
    void getProgressPercentage_WithFullProgress_Returns100() {
        Goal goal = Goal.builder()
                .currentValue(BigDecimal.valueOf(1000))
                .targetValue(BigDecimal.valueOf(1000))
                .unit("R$")
                .status(GoalStatus.IN_PROGRESS)
                .build();

        BigDecimal percentage = goal.getProgressPercentage();

        assertEquals(0, percentage.compareTo(BigDecimal.valueOf(100.0)),
                "1000/1000 deve ser 100%");
    }

    @Test
    void getProgressPercentage_ExceedingTarget_CappedAt100() {
        Goal goal = Goal.builder()
                .currentValue(BigDecimal.valueOf(1500))
                .targetValue(BigDecimal.valueOf(1000))
                .unit("R$")
                .status(GoalStatus.IN_PROGRESS)
                .build();

        BigDecimal percentage = goal.getProgressPercentage();

        assertEquals(0, percentage.compareTo(BigDecimal.valueOf(100.0)),
                "Progresso acima de 100% deve ser capado em 100%");
    }

    @Test
    void getProgressPercentage_WithTargetZero_ThrowsArithmeticException() {
        Goal goal = Goal.builder()
                .currentValue(BigDecimal.ZERO)
                .targetValue(BigDecimal.ZERO)
                .unit("R$")
                .status(GoalStatus.IN_PROGRESS)
                .build();

        assertThrows(ArithmeticException.class,
                goal::getProgressPercentage,
                "Divisão por zero (targetValue=0) deve lançar ArithmeticException");
    }
}
