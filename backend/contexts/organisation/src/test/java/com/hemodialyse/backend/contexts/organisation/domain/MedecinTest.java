package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MedecinTest {
    @Test
    void rejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> new Medecin(null, new CentreId(UUID.randomUUID()), "Nom", null, null));
    }

    @Test
    void rejectsBlankNom() {
        assertThrows(IllegalArgumentException.class, () -> new Medecin(new MedecinId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "  ", null, null));
    }

    @Test
    void creates() {
        Medecin m = new Medecin(new MedecinId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "Dupont", "Jean", "Nephro");
        assertEquals("Dupont", m.nom());
    }
}
