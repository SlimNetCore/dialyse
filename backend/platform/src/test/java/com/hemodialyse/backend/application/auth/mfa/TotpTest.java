package com.hemodialyse.backend.application.auth.mfa;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {

    /**
     * Secret ASCII « 12345678901234567890 » des vecteurs de test de la RFC 6238, en Base32.
     */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matches_the_rfc_6238_test_vectors_on_six_digits() {
        assertEquals("287082", Totp.codeAt(RFC_SECRET, Totp.stepAt(59)));
        assertEquals("081804", Totp.codeAt(RFC_SECRET, Totp.stepAt(1111111109L)));
        assertEquals("050471", Totp.codeAt(RFC_SECRET, Totp.stepAt(1111111111L)));
        assertEquals("005924", Totp.codeAt(RFC_SECRET, Totp.stepAt(1234567890L)));
        assertEquals("279037", Totp.codeAt(RFC_SECRET, Totp.stepAt(2000000000L)));
    }

    @Test
    void verifies_within_one_step_of_clock_drift_only() {
        String secret = Totp.generateSecret();
        long now = 1_700_000_000L;
        long step = Totp.stepAt(now);
        assertEquals(OptionalLong.of(step), Totp.verify(secret, Totp.codeAt(secret, step), now));
        assertEquals(OptionalLong.of(step - 1), Totp.verify(secret, Totp.codeAt(secret, step - 1), now));
        assertEquals(OptionalLong.of(step + 1), Totp.verify(secret, Totp.codeAt(secret, step + 1), now));
        assertTrue(Totp.verify(secret, Totp.codeAt(secret, step + 2), now).isEmpty());
        assertTrue(Totp.verify(secret, Totp.codeAt(secret, step - 2), now).isEmpty());
    }

    @Test
    void rejects_malformed_codes() {
        String secret = Totp.generateSecret();
        for (String bad : new String[]{null, "", "12345", "1234567", "abcdef", "12 456"}) {
            assertTrue(Totp.verify(secret, bad, 1_700_000_000L).isEmpty(), bad);
        }
    }

    @Test
    void secrets_are_random_base32_and_round_trip() {
        String a = Totp.generateSecret();
        String b = Totp.generateSecret();
        assertEquals(32, a.length());
        assertTrue(a.matches("[A-Z2-7]+"));
        assertNotEquals(a, b);
        assertEquals(a, Totp.base32Encode(Totp.base32Decode(a)));
    }

    @Test
    void builds_an_otpauth_uri() {
        String uri = Totp.otpauthUri("HemoDialyse", "jean dupont", "ABCDEFGH");
        assertTrue(uri.startsWith("otpauth://totp/HemoDialyse:jean%20dupont?secret=ABCDEFGH"));
        assertTrue(uri.contains("issuer=HemoDialyse"));
    }
}
