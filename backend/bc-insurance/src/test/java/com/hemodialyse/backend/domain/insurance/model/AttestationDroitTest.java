package com.hemodialyse.backend.domain.insurance.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttestationDroitTest {
    @Test
    void should_reject_null_dates() {
        assertThrows(IllegalArgumentException.class, () ->
                new AttestationDroit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, LocalDate.now()));
    }

    @Test
    void should_reject_fin_before_debut() {
        assertThrows(IllegalArgumentException.class, () ->
                new AttestationDroit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void should_create_with_valid_dates() {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 12, 31);
        AttestationDroit a = new AttestationDroit(id, patientId, centerId, debut, fin);
        assertEquals(id, a.getId());
        assertEquals(patientId, a.getPatientId());
        assertEquals(centerId, a.getCenterId());
        assertEquals(debut, a.getDateDebut());
        assertEquals(fin, a.getDateFin());
    }
}
