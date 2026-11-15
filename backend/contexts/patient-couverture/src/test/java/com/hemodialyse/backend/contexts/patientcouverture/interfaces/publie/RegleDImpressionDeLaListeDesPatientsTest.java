package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer.PatientImprime;
import com.hemodialyse.backend.contexts.patientcouverture.contrat.ListeDesPatientsAImprimerEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** DOC5f (ADR 0198) : la règle d'impression pose les lignes de la liste, champ par champ, sans garde. */
class RegleDImpressionDeLaListeDesPatientsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000f5a01");

    private final ListeDesPatientsAImprimerEnMemoire patients = new ListeDesPatientsAImprimerEnMemoire();
    private final RegleDImpressionDeLaListeDesPatients regle = new RegleDImpressionDeLaListeDesPatients(patients);

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lignes() {
        Map<String, Object> donnees = regle.donneesDuServeur(CENTRE, Map.of("CENTER_ID", "ignore"));
        assertThat(donnees).containsOnlyKeys("LIGNES");
        return (List<Map<String, Object>>) donnees.get("LIGNES");
    }

    @Test
    void should_supply_the_rows_with_the_template_field_names_and_types() {
        patients.poser(new CentreId(CENTRE), new PatientImprime("P-1", "Amrani", "Sara", "F", "A+", LocalDate.of(1980, 1, 2),
                LocalDate.of(2026, 3, 4), "ASS-1", "0555", "ACTIF"));

        assertThat(lignes()).hasSize(1);
        assertThat(lignes().getFirst()).containsOnlyKeys("CODE_PATIENT", "NOM", "PRENOM", "SEXE", "GROUPE_SANGUIN", "DATE_NAISSANCE",
                        "DATE_ADMISSION", "NUMERO_ASSURANCE", "TEL_MOBILE", "ETAT_PATIENT")
                .containsEntry("CODE_PATIENT", "P-1").containsEntry("NOM", "Amrani").containsEntry("PRENOM", "Sara")
                .containsEntry("SEXE", "F").containsEntry("GROUPE_SANGUIN", "A+")
                .containsEntry("DATE_NAISSANCE", java.sql.Date.valueOf("1980-01-02"))
                .containsEntry("DATE_ADMISSION", java.sql.Date.valueOf("2026-03-04")).containsEntry("NUMERO_ASSURANCE", "ASS-1")
                .containsEntry("TEL_MOBILE", "0555").containsEntry("ETAT_PATIENT", "ACTIF");
    }

    @Test
    void should_keep_absent_values_null() {
        patients.poser(new CentreId(CENTRE), new PatientImprime(null, "Zahi", "Zoe", "M", null, null, LocalDate.of(2026, 3, 4), "ASS-9",
                null, null));

        assertThat(lignes().getFirst()).containsEntry("DATE_NAISSANCE", null).containsEntry("CODE_PATIENT", null)
                .containsEntry("ETAT_PATIENT", null);
    }

    @Test
    void should_supply_an_empty_list_for_a_centre_without_patient() {
        assertThat(lignes()).isEmpty();
    }

    @Test
    void should_not_guard_the_type_as_before_the_conversion() {
        assertThat(regle.type()).isEqualTo("LISTE_PATIENTS");
        assertThat(regle.gardeAussiParLeModele()).isFalse();
        assertThatCode(() -> regle.garder(null)).doesNotThrowAnyException();
        assertThatCode(() -> regle.garder(CENTRE)).doesNotThrowAnyException();
    }
}
