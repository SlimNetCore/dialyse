package com.hemodialyse.backend.domain.patient.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void shouldNormalizeEmailToLowercase() {
        Email email = new Email("  TEST.User+Tag@Example.COM ");
        assertEquals("test.user+tag@example.com", email.value());
    }

    @Test
    void shouldRejectInvalidEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
        assertEquals("Email invalide", ex.getMessage());
    }

    @Test
    void shouldRejectBlankEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new Email("   "));
        assertEquals("Email obligatoire", ex.getMessage());
    }
}

