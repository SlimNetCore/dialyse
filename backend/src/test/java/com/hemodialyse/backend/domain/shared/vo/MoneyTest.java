package com.hemodialyse.backend.domain.shared.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void rejectsNullAmount() {
        assertThrows(BusinessException.class, () -> Money.of(null));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(BusinessException.class, () -> Money.of(new BigDecimal("-0.01")));
    }

    @Test
    void addPreservesValue() {
        Money result = Money.of(new BigDecimal("10.50")).add(Money.of(new BigDecimal("2.25")));
        assertEquals(0, new BigDecimal("12.75").compareTo(result.amount()));
    }

    @Test
    void timesAppliesUnitPriceToQuantity() {
        Money result = Money.of(new BigDecimal("1.5")).times(Quantite.of(new BigDecimal("3")));
        assertEquals(0, new BigDecimal("4.5").compareTo(result.amount()));
    }

    @Test
    void equalityIsValueBasedIgnoringScale() {
        assertEquals(Money.of(new BigDecimal("3.0")), Money.of(new BigDecimal("3.00")));
        assertNotEquals(Money.of(new BigDecimal("3.0")), Money.of(new BigDecimal("3.01")));
    }

    @Test
    void zeroIsNeutralForAddition() {
        Money value = Money.of(new BigDecimal("7.20"));
        assertEquals(Money.zero().add(value), value);
    }
}

