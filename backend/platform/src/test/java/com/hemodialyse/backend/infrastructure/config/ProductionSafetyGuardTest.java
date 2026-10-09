package com.hemodialyse.backend.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSafetyGuardTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String POSTGRES = "jdbc:postgresql://postgres:5432/hemodialyse";

    @Test
    void a_strong_secret_and_a_postgres_database_are_accepted() {
        assertTrue(ProductionSafetyGuard.violations(SECRET, POSTGRES).isEmpty());
        new ProductionSafetyGuard(SECRET, POSTGRES);
    }

    @Test
    void the_development_secret_is_refused() {
        var violations = ProductionSafetyGuard.violations("change-me-change-me-change-me-123456", POSTGRES);

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("JWT_SECRET"));
    }

    @Test
    void a_missing_or_short_secret_is_refused() {
        assertEquals(1, ProductionSafetyGuard.violations("", POSTGRES).size());
        assertEquals(1, ProductionSafetyGuard.violations(null, POSTGRES).size());
        assertTrue(ProductionSafetyGuard.violations("trop-court", POSTGRES).get(0).contains("trop court"));
    }

    @Test
    void an_in_memory_h2_database_is_refused() {
        var violations = ProductionSafetyGuard.violations(SECRET, "jdbc:h2:mem:hemodialyse;MODE=PostgreSQL");

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("H2"));
    }

    @Test
    void startup_fails_with_every_violation_listed() {
        var erreur = assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGuard("change-me-x", "jdbc:h2:mem:x"));

        assertTrue(erreur.getMessage().contains("JWT_SECRET"));
        assertTrue(erreur.getMessage().contains("H2"));
    }
}
