package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PatientTest {
    @Test
    void should_reject_sortie_sans_date() {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new Identite("Doe", "John", "M", LocalDate.of(1980, 1, 1)), "123", LocalDate.of(2026, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> p.declarerSortie(null));
    }

    @Test
    void should_admettre_patient_actif() {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new Identite("Doe", "John", "M", LocalDate.of(1980, 1, 1)), "123", LocalDate.of(2026, 1, 1));
        assertEquals(EtatPatient.ACTIF, p.etat());
        assertNull(p.dateSortie());
    }

    @Test
    void should_declarer_sortie_avec_date() {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new Identite("Doe", "John", "M", LocalDate.of(1980, 1, 1)), "123", LocalDate.of(2026, 1, 1));
        p.declarerSortie(LocalDate.of(2026, 2, 2));
        assertEquals(EtatPatient.SORTI, p.etat());
        assertEquals(LocalDate.of(2026, 2, 2), p.dateSortie());
    }

    @Test
    void should_reject_transfert_meme_centre() {
        UUID c = UUID.randomUUID();
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), new CentreId(c),
                new Identite("Doe", "John", "M", LocalDate.of(1980, 1, 1)), "123", LocalDate.of(2026, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> p.transferer(new CentreId(c)));
    }
}