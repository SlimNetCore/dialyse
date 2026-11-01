package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PecTest {
    @Test
    void should_create_in_cree_state() {
        Pec p = Pec.creer(new PecId(UUID.randomUUID()), new PatientId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, java.time.OffsetDateTime.now());
        assertEquals(StatutPec.CREE, p.statut());
        assertFalse(p.autoriseSeance());
    }

    @Test
    void should_reject_valider_if_not_cree() {
        Pec p = Pec.creer(new PecId(UUID.randomUUID()), new PatientId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, java.time.OffsetDateTime.now());
        p.valider(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        assertThrows(IllegalStateException.class, () -> p.valider(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null));
    }

    @Test
    void should_reject_cloturer_if_not_validee() {
        Pec p = Pec.creer(new PecId(UUID.randomUUID()), new PatientId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, java.time.OffsetDateTime.now());
        assertThrows(IllegalStateException.class, () -> p.cloturer());
    }

    @Test
    void should_cycle_cree_validee_cloturee() {
        Pec p = Pec.creer(new PecId(UUID.randomUUID()), new PatientId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, java.time.OffsetDateTime.now());
        p.valider(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        assertEquals(StatutPec.VALIDEE, p.statut());
        assertTrue(p.autoriseSeance());
        p.cloturer();
        assertEquals(StatutPec.CLOTUREE, p.statut());
        assertFalse(p.autoriseSeance());
    }

    @Test
    void should_reject_incoherent_periode() {
        assertThrows(IllegalArgumentException.class, () -> Pec.creer(new PecId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null, java.time.OffsetDateTime.now()));
    }
}