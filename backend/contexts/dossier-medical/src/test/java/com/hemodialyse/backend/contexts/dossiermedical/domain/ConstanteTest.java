package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstanteTest {

    @Test
    void reject_null_centre() {
        assertThrows(IllegalArgumentException.class, () ->
                new Constante(new ConstanteId(UUID.randomUUID()), UUID.randomUUID(), null, Instant.now(), 70.0, "12/8", 70, 36.5));
    }

    @Test
    void accept_valid() {
        new Constante(new ConstanteId(UUID.randomUUID()), UUID.randomUUID(), new CentreId(UUID.randomUUID()), Instant.now(), 70.0, "12/8", 70, 36.5);
    }
}