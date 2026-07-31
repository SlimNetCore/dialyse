package com.hemodialyse.backend.domain.shared.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoidsTest {

    @Test
    void acceptsPlausibleWeight() {
        assertEquals(0, new BigDecimal("72.5").compareTo(Poids.ofKilogrammes(new BigDecimal("72.5")).kilogrammes()));
    }

    @Test
    void rejectsNull() {
        assertThrows(BusinessException.class, () -> Poids.ofKilogrammes(null));
    }

    @Test
    void rejectsZeroOrNegative() {
        assertThrows(BusinessException.class, () -> Poids.ofKilogrammes(BigDecimal.ZERO));
        assertThrows(BusinessException.class, () -> Poids.ofKilogrammes(new BigDecimal("-1")));
    }

    @Test
    void rejectsImplausiblyLargeWeight() {
        assertThrows(BusinessException.class, () -> Poids.ofKilogrammes(new BigDecimal("501")));
    }

    @Test
    void equalityIsValueBased() {
        assertEquals(Poids.ofKilogrammes(new BigDecimal("70.0")), Poids.ofKilogrammes(new BigDecimal("70.00")));
    }
}

