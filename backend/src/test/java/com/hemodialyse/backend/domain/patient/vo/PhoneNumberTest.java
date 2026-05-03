package com.hemodialyse.backend.domain.patient.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberTest {

    @Test
    void shouldNormalizeFrenchLikeNumber() {
        PhoneNumber phone = new PhoneNumber(" 06 12-34-56-78 ");
        assertEquals("0612345678", phone.value());
    }

    @Test
    void shouldConvertDoubleZeroPrefixToPlus() {
        PhoneNumber phone = new PhoneNumber("00213 555 12 34 56");
        assertEquals("+213555123456", phone.value());
    }

    @Test
    void shouldRejectInvalidPhone() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("abc"));
        assertEquals("Téléphone invalide", ex.getMessage());
    }
}

