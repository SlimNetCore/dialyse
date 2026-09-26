package com.hemodialyse.backend.application.direction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DirectionPoliciesTest {

    @Test
    void small_headcounts_are_masked_but_zero_and_large_counts_are_kept() {
        assertEquals(0L, AnonymityPolicy.mask(0));
        assertNull(AnonymityPolicy.mask(1));
        assertNull(AnonymityPolicy.mask(AnonymityPolicy.THRESHOLD - 1));
        assertEquals(AnonymityPolicy.THRESHOLD, AnonymityPolicy.mask(AnonymityPolicy.THRESHOLD));
        assertEquals(250L, AnonymityPolicy.mask(250));
    }

    @Test
    void a_strong_password_is_accepted() {
        assertNull(PasswordPolicy.violation("Correct-Horse-42", "direction"));
    }

    @Test
    void weak_passwords_are_refused_with_a_stable_code() {
        assertEquals("PASSWORD_TOO_SHORT", PasswordPolicy.violation("Abc123", "u"));
        assertEquals("PASSWORD_TOO_SHORT", PasswordPolicy.violation(null, "u"));
        assertEquals("PASSWORD_WEAK", PasswordPolicy.violation("onlyletterslonger", "u"));
        assertEquals("PASSWORD_WEAK", PasswordPolicy.violation("123456789012345", "u"));
        assertEquals("PASSWORD_CONTAINS_USERNAME", PasswordPolicy.violation("Direction-2026x", "direction"));
        assertEquals("PASSWORD_TOO_LONG", PasswordPolicy.violation("a1".repeat(60), "u"));
    }
}
