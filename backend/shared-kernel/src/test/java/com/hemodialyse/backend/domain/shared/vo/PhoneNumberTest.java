package com.hemodialyse.backend.domain.shared.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberTest {

    @Test
    void normalizesSpacingAndSeparators() {
        PhoneNumber phone = new PhoneNumber(" 06 12-34-56-78 ");
        assertEquals("0612345678", phone.value());
    }

    @Test
    void convertsInternationalPrefix() {
        PhoneNumber phone = new PhoneNumber("00213 555 12 34 56");
        assertEquals("+213555123456", phone.value());
    }

    @Test
    void rejectsInvalidNumber() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("abc"));
        assertEquals("Téléphone invalide", ex.getMessage());
    }
}

