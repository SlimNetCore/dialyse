package com.hemodialyse.backend.application.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolitiqueMotDePasseTest {

    @Test
    void a_long_password_with_a_letter_and_a_digit_is_accepted() {
        assertTrue(PolitiqueMotDePasse.refus("Nouveau-mdp-2026", "sara").isEmpty());
        assertTrue(PolitiqueMotDePasse.refus("abcdefghi1", null).isEmpty());
    }

    @Test
    void short_letterless_digitless_or_login_containing_passwords_are_refused() {
        for (String refuse : new String[]{null, "", "abc1", "abcdefghi", "1234567890", "xxSARAxx123456"}) {
            assertEquals("PASSWORD_TROP_FAIBLE", PolitiqueMotDePasse.refus(refuse, "sara").orElse("accepte"), refuse);
        }
    }

    @Test
    void a_password_longer_than_the_maximum_is_refused() {
        assertEquals("PASSWORD_TROP_FAIBLE",
                PolitiqueMotDePasse.refus("a1".repeat(PolitiqueMotDePasse.LONGUEUR_MAX), "sara").orElse("accepte"));
    }
}
