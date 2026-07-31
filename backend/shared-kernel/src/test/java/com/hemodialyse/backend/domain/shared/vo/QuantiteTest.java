package com.hemodialyse.backend.domain.shared.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantiteTest {

    @Test
    void rejectsNullValue() {
        assertThrows(BusinessException.class, () -> Quantite.of(null));
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(BusinessException.class, () -> Quantite.of(new BigDecimal("-1")));
    }

    @Test
    void zeroIsAllowedButNotPositive() {
        assertFalse(Quantite.zero().isPositive());
    }

    @Test
    void positiveIsDetected() {
        assertTrue(Quantite.of(new BigDecimal("0.5")).isPositive());
    }

    @Test
    void addSumsValues() {
        Quantite result = Quantite.of(new BigDecimal("2")).add(Quantite.of(new BigDecimal("3")));
        assertEquals(0, new BigDecimal("5").compareTo(result.value()));
    }
}

