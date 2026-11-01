package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrescriptionTest {

    @Test
    void reject_null_centre() {
        assertThrows(IllegalArgumentException.class, () ->
                new Prescription(new PrescriptionId(UUID.randomUUID()), UUID.randomUUID(), null,
                        "Medicament", "1 cp/j", "30j", Instant.now()));
    }

    @Test
    void accept_valid() {
        Prescription p = new Prescription(new PrescriptionId(UUID.randomUUID()), UUID.randomUUID(),
                new CentreId(UUID.randomUUID()), "Medicament", "1 cp/j", "30j", Instant.now());
        assertEquals(StatutPrescription.BROUILLON, p.statut());
    }

    @Test
    void reject_double_signature() {
        Prescription p = new Prescription(new PrescriptionId(UUID.randomUUID()), UUID.randomUUID(),
                new CentreId(UUID.randomUUID()), "Medicament", "1 cp/j", "30j", Instant.now());
        p.signer();
        assertEquals(StatutPrescription.SIGNEE, p.statut());
        assertThrows(IllegalStateException.class, p::signer);
    }
}