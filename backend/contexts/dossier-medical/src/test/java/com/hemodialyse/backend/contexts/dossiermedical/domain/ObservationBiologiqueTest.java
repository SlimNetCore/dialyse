package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservationBiologiqueTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final UUID PATIENT = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DATE_OBSERVATION = LocalDate.of(2026, 3, 10);

    private ObservationBiologique observationValide() {
        return ObservationBiologique.enregistrer(
                new ObservationBiologiqueId(UUID.randomUUID()), PATIENT, CENTRE,
                "HGB", "Hémoglobine", "11.5", "g/dL",
                12.0, 16.0, DATE_OBSERVATION, "MED-001", "Labo Central", "Bilan", MAINTENANT);
    }

    @Test
    void enregistre_observation_valide() {
        ObservationBiologique o = observationValide();
        assertEquals("HGB", o.code());
        assertEquals("11.5", o.valeur());
        assertEquals(CENTRE, o.centreId());
        assertEquals(PATIENT, o.patientId());
    }

    @Test
    void rejeter_centre_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationBiologique.enregistrer(new ObservationBiologiqueId(UUID.randomUUID()),
                        PATIENT, null, "HGB", null, "11.5", null, null, null,
                        DATE_OBSERVATION, null, null, null, MAINTENANT));
    }

    @Test
    void rejeter_patient_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationBiologique.enregistrer(new ObservationBiologiqueId(UUID.randomUUID()),
                        null, CENTRE, "HGB", null, "11.5", null, null, null,
                        DATE_OBSERVATION, null, null, null, MAINTENANT));
    }

    @Test
    void rejeter_code_vide() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationBiologique.enregistrer(new ObservationBiologiqueId(UUID.randomUUID()),
                        PATIENT, CENTRE, "  ", null, "11.5", null, null, null,
                        DATE_OBSERVATION, null, null, null, MAINTENANT));
    }

    @Test
    void rejeter_valeur_vide() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationBiologique.enregistrer(new ObservationBiologiqueId(UUID.randomUUID()),
                        PATIENT, CENTRE, "HGB", null, " ", null, null, null,
                        DATE_OBSERVATION, null, null, null, MAINTENANT));
    }

    @Test
    void rejeter_date_future() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationBiologique.enregistrer(new ObservationBiologiqueId(UUID.randomUUID()),
                        PATIENT, CENTRE, "HGB", null, "11.5", null, null, null,
                        LocalDate.now(ZoneOffset.UTC).plusDays(1), null, null, null, MAINTENANT));
    }

    @Test
    void rejeter_seuils_incoherents() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationBiologique.enregistrer(new ObservationBiologiqueId(UUID.randomUUID()),
                        PATIENT, CENTRE, "HGB", null, "11.5", null, 16.0, 12.0,
                        DATE_OBSERVATION, null, null, null, MAINTENANT));
    }

    @Test
    void seuils_optionnels_acceptes() {
        ObservationBiologique o = ObservationBiologique.enregistrer(
                new ObservationBiologiqueId(UUID.randomUUID()), PATIENT, CENTRE,
                "CRP", null, "3.2", "mg/L", null, null,
                DATE_OBSERVATION, null, null, null, MAINTENANT);
        assertEquals("CRP", o.code());
    }
}
