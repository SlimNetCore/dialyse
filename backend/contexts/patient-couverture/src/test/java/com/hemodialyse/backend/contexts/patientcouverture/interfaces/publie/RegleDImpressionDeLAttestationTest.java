package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer.AttestationImprimable;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLAttestationImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.contrat.AttestationAImprimerEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DOC5h (ADR 0200) : la règle d'impression pose la ligne de l'attestation, champ par champ, sans garde ; identifiants lus comme l'ancienne requête. */
class RegleDImpressionDeLAttestationTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000f5a81");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000f5a82");
    private static final UUID ATTESTATION = UUID.fromString("00000000-0000-0000-0000-0000000f5a83");
    private static final UUID AUTRE_ATTESTATION = UUID.fromString("00000000-0000-0000-0000-0000000f5a84");

    private final AttestationAImprimerEnMemoire attestations = new AttestationAImprimerEnMemoire();
    private Optional<String> nomDuCentre = Optional.of("Centre fictif");
    private final RegleDImpressionDeLAttestation regle = new RegleDImpressionDeLAttestation(
            new ComposerLAttestationImprimee(attestations, centre -> nomDuCentre));

    private static AttestationImprimable valeurs(String nom, LocalDate fin) {
        return new AttestationImprimable(nom, "Sara", "F", LocalDate.of(1980, 5, 6), "ASS-1", "Rue", "0550", LocalDate.of(2026, 1, 2), fin);
    }

    private static Map<String, String> parametres(String attestation, String patient) {
        Map<String, String> m = new HashMap<>();
        m.put("attestationId", attestation);
        m.put("patientId", patient);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lignes(Map<String, String> parametres) {
        Map<String, Object> donnees = regle.donneesDuServeur(CENTRE, parametres);
        assertThat(donnees).containsOnlyKeys("LIGNES");
        return (List<Map<String, Object>>) donnees.get("LIGNES");
    }

    @Test
    void should_supply_one_row_with_the_template_field_names_and_types() {
        attestations.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, ATTESTATION, valeurs("Amrani", LocalDate.of(2027, 1, 2)));

        List<Map<String, Object>> lignes = lignes(parametres(ATTESTATION.toString(), null));

        assertThat(lignes).hasSize(1);
        assertThat(lignes.getFirst()).containsOnlyKeys("NOM", "PRENOM", "SEXE", "DATE_NAISSANCE", "NUMERO_ASSURANCE", "ADRESSE", "TEL_MOBILE",
                        "DATE_DEBUT", "DATE_FIN", "CENTER_NAME")
                .containsEntry("NOM", "Amrani").containsEntry("PRENOM", "Sara").containsEntry("SEXE", "F")
                .containsEntry("DATE_NAISSANCE", java.sql.Date.valueOf("1980-05-06")).containsEntry("NUMERO_ASSURANCE", "ASS-1")
                .containsEntry("ADRESSE", "Rue").containsEntry("TEL_MOBILE", "0550").containsEntry("DATE_DEBUT", java.sql.Date.valueOf("2026-01-02"))
                .containsEntry("DATE_FIN", java.sql.Date.valueOf("2027-01-02")).containsEntry("CENTER_NAME", "Centre fictif");
    }

    @Test
    void should_keep_absent_values_null() {
        attestations.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, ATTESTATION,
                new AttestationImprimable("Zahi", "Zoe", "M", null, "", null, null, LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, 2)));

        assertThat(lignes(parametres(ATTESTATION.toString(), null)).getFirst()).containsEntry("DATE_NAISSANCE", null)
                .containsEntry("ADRESSE", null).containsEntry("TEL_MOBILE", null);
    }

    @Test
    void should_read_the_attestation_first_and_the_latest_of_the_patient_otherwise() {
        attestations.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, ATTESTATION, valeurs("Ancienne", LocalDate.of(2027, 1, 2)));
        attestations.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, AUTRE_ATTESTATION, valeurs("Recente", LocalDate.of(2027, 6, 2)));

        assertThat(lignes(parametres(null, PATIENT.toString())).getFirst()).containsEntry("NOM", "Recente");
        assertThat(lignes(parametres("", PATIENT.toString())).getFirst()).containsEntry("NOM", "Recente");
        assertThat(lignes(parametres(ATTESTATION.toString(), PATIENT.toString())).getFirst()).containsEntry("NOM", "Ancienne");
    }

    @Test
    void should_not_fall_back_to_the_patient_when_the_attestation_is_unknown() {
        attestations.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, ATTESTATION, valeurs("Amrani", LocalDate.of(2027, 1, 2)));

        assertThat(lignes(parametres(UUID.randomUUID().toString(), PATIENT.toString()))).isEmpty();
    }

    @Test
    void should_supply_no_row_without_identifier_or_for_an_unknown_centre() {
        attestations.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, ATTESTATION, valeurs("Amrani", LocalDate.of(2027, 1, 2)));

        assertThat(lignes(parametres(null, null))).isEmpty();
        assertThat(lignes(parametres("", ""))).isEmpty();
        nomDuCentre = Optional.empty();
        assertThat(lignes(parametres(ATTESTATION.toString(), null))).isEmpty();
    }

    @Test
    void should_fail_like_the_old_cast_for_an_unreadable_identifier_that_is_read() {
        assertThatThrownBy(() -> lignes(parametres("pas-un-uuid", null))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Error executing SQL statement for: attestation.");
        assertThatThrownBy(() -> lignes(parametres(null, "pas-un-uuid"))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Error executing SQL statement for: attestation.");
        assertThatThrownBy(() -> lignes(parametres("pas-un-uuid", PATIENT.toString()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_read_the_patient_identifier_when_the_attestation_is_given() {
        assertThatCode(() -> lignes(parametres(ATTESTATION.toString(), "pas-un-uuid"))).doesNotThrowAnyException();
    }

    @Test
    void should_not_guard_the_type_as_before_the_conversion() {
        assertThat(regle.type()).isEqualTo("ATTESTATION");
        assertThat(regle.gardeAussiParLeModele()).isFalse();
        assertThatCode(() -> regle.garder(null)).doesNotThrowAnyException();
        assertThatCode(() -> regle.garder(CENTRE)).doesNotThrowAnyException();
    }
}
