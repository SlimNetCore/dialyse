package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForfaitAppliqueTest {
    @Test
    void should_create() {
        ForfaitApplique f = new ForfaitApplique(UUID.randomUUID(), "HD", "Forfait HD", new BigDecimal("100"),
                "admin", OffsetDateTime.now());
        assertEquals("HD", f.code());
    }

    @Test
    void should_reject_blank_code() {
        assertThrows(IllegalArgumentException.class, () -> new ForfaitApplique(UUID.randomUUID(), " ", "lib", BigDecimal.ZERO,
                "admin", OffsetDateTime.now()));
    }
}