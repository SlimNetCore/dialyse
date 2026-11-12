package com.hemodialyse.backend.application.direction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DirectionPoliciesTest {

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

    @Test
    void the_owner_password_policy_is_stricter() {
        assertNull(PasswordPolicy.ownerViolation("Proprietaire#Solide-2026", "owner"));
        assertEquals("PASSWORD_TOO_SHORT", PasswordPolicy.ownerViolation("Aa1!aaaaaaaa", "u"));
        assertEquals("PASSWORD_WEAK", PasswordPolicy.ownerViolation("proprietaire-solide-2026", "u"));
        assertEquals("PASSWORD_WEAK", PasswordPolicy.ownerViolation("ProprietaireSolide2026", "u"));
        assertEquals("PASSWORD_CONTAINS_USERNAME", PasswordPolicy.ownerViolation("Zz-Owner-Secret-2026", "owner"));
    }
}
