package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLaFicheSignaletiqueImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer.FicheImprimable;
import com.hemodialyse.backend.contexts.patientcouverture.contrat.FicheSignaletiqueAImprimerEnMemoire;
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

/** DOC5i (ADR 0201) : la règle d'impression pose la ligne de la fiche, champ par champ, sans garde ; identifiant lu comme l'ancienne requête. */
class RegleDImpressionDeLaFicheSignaletiqueTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000f5c81");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000f5c82");

    private final FicheSignaletiqueAImprimerEnMemoire fiches = new FicheSignaletiqueAImprimerEnMemoire();
    private Optional<String> nomDuCentre = Optional.of("Centre fictif");
    private final RegleDImpressionDeLaFicheSignaletique regle = new RegleDImpressionDeLaFicheSignaletique(
            new ComposerLaFicheSignaletiqueImprimee(fiches, centre -> nomDuCentre));

    private static FicheImprimable fiche() {
        return new FicheImprimable("P-001", "Mme", "Amrani", "Sara", "F", "O+", LocalDate.of(1980, 5, 6), "Béjaïa",
                LocalDate.of(2026, 3, 4), 3, "Mariée", "Institutrice", "12 rue", "021", "055", "022", "s@x.test", "ASS-1", "ACTIF",
                "Conjoint", "Obs", true, "Amrani", "Karim");
    }

    private static Map<String, String> parametres(String patient) {
        Map<String, String> m = new HashMap<>();
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
        fiches.poser(new CentreId(CENTRE), PATIENT, fiche());

        List<Map<String, Object>> lignes = lignes(parametres(PATIENT.toString()));

        assertThat(lignes).hasSize(1);
        assertThat(lignes.getFirst()).hasSize(25).containsEntry("CODE_PATIENT", "P-001").containsEntry("CIVILITE", "Mme")
                .containsEntry("NOM", "Amrani").containsEntry("PRENOM", "Sara").containsEntry("SEXE", "F")
                .containsEntry("GROUPE_SANGUIN", "O+").containsEntry("DATE_NAISSANCE", java.sql.Date.valueOf("1980-05-06"))
                .containsEntry("LIEU_NAISSANCE", "Béjaïa").containsEntry("DATE_ADMISSION", java.sql.Date.valueOf("2026-03-04"))
                .containsEntry("NOMBRE_ENFANTS", 3).containsEntry("SITUATION_FAMILIALE", "Mariée")
                .containsEntry("PROFESSION1", "Institutrice").containsEntry("ADRESSE", "12 rue").containsEntry("TEL_PERSONNEL", "021")
                .containsEntry("TEL_MOBILE", "055").containsEntry("TEL_BUREAU", "022").containsEntry("EMAIL", "s@x.test")
                .containsEntry("NUMERO_ASSURANCE", "ASS-1").containsEntry("ETAT_PATIENT", "ACTIF")
                .containsEntry("QUALITE_ASSURE", "Conjoint").containsEntry("OBSERVATION", "Obs").containsEntry("SOUS_KT", true)
                .containsEntry("ASSURE_NOM", "Amrani").containsEntry("ASSURE_PRENOM", "Karim")
                .containsEntry("CENTER_NAME", "Centre fictif");
    }

    @Test
    void should_keep_absent_values_null() {
        fiches.poser(new CentreId(CENTRE), PATIENT, new FicheImprimable(null, null, "Zahi", "Zoe", "M", null, null, null,
                LocalDate.of(2026, 3, 4), null, null, null, null, null, null, null, null, "", null, null, null, null, null, null));

        assertThat(lignes(parametres(PATIENT.toString())).getFirst()).containsEntry("DATE_NAISSANCE", null)
                .containsEntry("NOMBRE_ENFANTS", null).containsEntry("SOUS_KT", null).containsEntry("CODE_PATIENT", null);
    }

    @Test
    void should_still_print_the_sheet_without_a_centre_name_like_the_old_left_join() {
        fiches.poser(new CentreId(CENTRE), PATIENT, fiche());
        nomDuCentre = Optional.empty();

        assertThat(lignes(parametres(PATIENT.toString())).getFirst()).containsEntry("CENTER_NAME", null).containsEntry("NOM", "Amrani");
    }

    @Test
    void should_supply_no_row_for_an_unknown_patient_or_when_none_is_given() {
        fiches.poser(new CentreId(CENTRE), PATIENT, fiche());

        assertThat(lignes(parametres(UUID.randomUUID().toString()))).isEmpty();
        assertThat(lignes(parametres(null))).isEmpty();
        assertThat(lignes(new HashMap<>())).isEmpty();
    }

    @Test
    void should_fail_like_the_old_cast_for_an_empty_or_unreadable_identifier() {
        assertThatThrownBy(() -> lignes(parametres("pas-un-uuid"))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Error executing SQL statement for: fiche_patient.");
        assertThatThrownBy(() -> lignes(parametres(""))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Error executing SQL statement for: fiche_patient.");
    }

    @Test
    void should_not_guard_the_type_as_before_the_conversion() {
        assertThat(regle.type()).isEqualTo("FICHE_PATIENT");
        assertThat(regle.gardeAussiParLeModele()).isFalse();
        assertThatCode(() -> regle.garder(null)).doesNotThrowAnyException();
        assertThatCode(() -> regle.garder(CENTRE)).doesNotThrowAnyException();
    }
}
