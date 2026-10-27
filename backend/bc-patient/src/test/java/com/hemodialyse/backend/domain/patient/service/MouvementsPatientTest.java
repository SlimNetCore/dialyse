package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.patient.model.MouvementPatient;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.patient.vo.JoursDialyse;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MouvementsPatientTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 4);
    private static final Instant MAINTENANT = Instant.parse("2026-10-04T10:00:00Z");

    private static Patient patientPlace() {
        Patient p = Patient.creer(CenterId.of(UUID.randomUUID()), "Nom", "Prenom", "M", LocalDate.of(2026, 1, 2),
                LocalDate.of(1970, 5, 5), new NumeroAssurance("ASS-1234567"), null);
        p.setSalleId(UUID.randomUUID());
        p.setPositionId(UUID.randomUUID());
        p.setGenerateurId(UUID.randomUUID());
        p.setJoursDialyse(new JoursDialyse(false, true, false, true, false, true, false));
        return p;
    }

    @Test
    void the_admission_records_the_admission_date_and_the_initial_assignment() {
        Patient p = patientPlace();

        MouvementPatient m = MouvementsPatient.admission(p, MAINTENANT);

        assertEquals(TypeMouvementPatient.ADMISSION, m.type());
        assertEquals(LocalDate.of(2026, 1, 2), m.dateEffet());
        assertNull(m.etatPrecedent());
        assertEquals("PERMANENT", m.etatNouveau());
        assertEquals("LUNDI,MERCREDI,VENDREDI", m.joursDialyse());
        assertFalse(m.automatique());
        assertEquals(p.getId().value(), m.patientId());
        assertEquals(p.getCenterId().value(), m.centerId());
    }

    @Test
    void a_state_change_yields_the_movement_of_the_new_state_dated_by_the_event() {
        Patient p = patientPlace();
        p.setEtatPatient("TRANSFERE");
        p.setDateEvenementEtat(LocalDate.of(2026, 10, 20));

        MouvementPatient m = MouvementsPatient.changementEtat(p, "PERMANENT", null, AUJOURDHUI, MAINTENANT).orElseThrow();

        assertEquals(TypeMouvementPatient.TRANSFERT, m.type());
        assertEquals(LocalDate.of(2026, 10, 20), m.dateEffet());
        assertEquals("PERMANENT", m.etatPrecedent());
        assertEquals("TRANSFERE", m.etatNouveau());
    }

    @Test
    void each_state_maps_to_its_movement_type() {
        assertEquals(TypeMouvementPatient.DECES, TypeMouvementPatient.pourEtat("DECEDE"));
        assertEquals(TypeMouvementPatient.GREFFE, TypeMouvementPatient.pourEtat("GREFFE"));
        assertEquals(TypeMouvementPatient.GUERISON, TypeMouvementPatient.pourEtat("GUERRI"));
        assertEquals(TypeMouvementPatient.SEJOUR_TEMPORAIRE, TypeMouvementPatient.pourEtat("VACANCIER_LOCAL"));
        assertEquals(TypeMouvementPatient.SEJOUR_TEMPORAIRE, TypeMouvementPatient.pourEtat("OCCASIONNEL"));
        assertEquals(TypeMouvementPatient.REPRISE, TypeMouvementPatient.pourEtat("PERMANENT"));
        assertTrue(TypeMouvementPatient.DECES.sortie());
        assertFalse(TypeMouvementPatient.SEJOUR_TEMPORAIRE.sortie());
    }

    @Test
    void a_resume_without_event_date_is_dated_today() {
        Patient p = patientPlace();   // PERMANENT, sans date

        MouvementPatient m = MouvementsPatient.changementEtat(p, "TRANSFERE", LocalDate.of(2026, 9, 1), AUJOURDHUI,
                MAINTENANT).orElseThrow();

        assertEquals(TypeMouvementPatient.REPRISE, m.type());
        assertEquals(AUJOURDHUI, m.dateEffet());
    }

    @Test
    void no_movement_when_neither_the_state_nor_the_date_changed() {
        Patient p = patientPlace();
        p.setEtatPatient("OCCASIONNEL");
        p.setDateEvenementEtat(LocalDate.of(2026, 10, 20));

        Optional<MouvementPatient> m = MouvementsPatient.changementEtat(p, "OCCASIONNEL", LocalDate.of(2026, 10, 20),
                AUJOURDHUI, MAINTENANT);

        assertTrue(m.isEmpty());
    }

    @Test
    void a_changed_end_date_is_a_movement() {
        Patient p = patientPlace();
        p.setEtatPatient("OCCASIONNEL");
        p.setDateEvenementEtat(LocalDate.of(2026, 11, 20));

        assertTrue(MouvementsPatient.changementEtat(p, "OCCASIONNEL", LocalDate.of(2026, 10, 20), AUJOURDHUI,
                MAINTENANT).isPresent());
    }

    @Test
    void aligning_the_returned_record_clears_the_assignment() {
        // la libération elle-même est dans contexts/planification (P7) ; la fiche renvoyée est seulement alignée
        Patient p = patientPlace();

        p.libererPlacement();

        assertNull(p.getSalleId());
        assertNull(p.getPositionId());
        assertNull(p.getGenerateurId());
        assertEquals(JoursDialyse.none(), p.getJoursDialyse());
    }
}
