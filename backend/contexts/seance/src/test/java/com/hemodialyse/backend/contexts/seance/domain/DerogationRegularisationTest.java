package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DerogationRegularisationTest {
    @Test
    void should_create_derogation() {
        DerogationRegularisation d = DerogationRegularisation.declarer(
                new DerogationId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new SeanceId(UUID.randomUUID()), "motif", "admin", OffsetDateTime.now());
        assertEquals("motif", d.motif());
    }

    @Test
    void should_reject_blank_motif() {
        assertThrows(IllegalArgumentException.class, () -> new DerogationRegularisation(
                new DerogationId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new SeanceId(UUID.randomUUID()), "  ", "admin", OffsetDateTime.now()));
    }
}