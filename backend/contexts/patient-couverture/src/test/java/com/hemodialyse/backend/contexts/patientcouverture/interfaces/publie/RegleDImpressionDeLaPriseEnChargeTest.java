package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLaPriseEnChargeImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer.PriseEnChargeImprimable;
import com.hemodialyse.backend.contexts.patientcouverture.contrat.PriseEnChargeAImprimerEnMemoire;
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

/** DOC5h (ADR 0200) : la règle d'impression pose la ligne de la prise en charge, champ par champ, sans garde ; identifiants lus comme l'ancienne requête. */
class RegleDImpressionDeLaPriseEnChargeTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000f5b81");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000f5b82");
    private static final UUID PEC = UUID.fromString("00000000-0000-0000-0000-0000000f5b83");
    private static final UUID AUTRE_PEC = UUID.fromString("00000000-0000-0000-0000-0000000f5b84");

    private final PriseEnChargeAImprimerEnMemoire prisesEnCharge = new PriseEnChargeAImprimerEnMemoire();
    private Optional<String> nomDuCentre = Optional.of("Centre fictif");
    private final RegleDImpressionDeLaPriseEnCharge regle = new RegleDImpressionDeLaPriseEnCharge(
            new ComposerLaPriseEnChargeImprimee(prisesEnCharge, centre -> nomDuCentre));

    private static PriseEnChargeImprimable valeurs(String nom, LocalDate fin) {
        return new PriseEnChargeImprimable(nom, "Sara", "ASS-1", LocalDate.of(1980, 5, 6), LocalDate.of(2026, 1, 2), fin, "VALIDEE",
                LocalDate.of(2026, 2, 3), LocalDate.of(2026, 12, 30));
    }

    private static Map<String, String> parametres(String pec, String patient) {
        Map<String, String> m = new HashMap<>();
        m.put("pecId", pec);
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
        prisesEnCharge.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, PEC, valeurs("Amrani", LocalDate.of(2027, 1, 2)));

        List<Map<String, Object>> lignes = lignes(parametres(PEC.toString(), null));

        assertThat(lignes).hasSize(1);
        assertThat(lignes.getFirst()).containsOnlyKeys("NOM", "PRENOM", "NUMERO_ASSURANCE", "DATE_NAISSANCE", "DATE_DEBUT_DEMANDE",
                        "DATE_FIN_DEMANDE", "STATUT", "DATE_DEBUT_EFFECTIF", "DATE_FIN_EFFECTIF", "CENTER_NAME")
                .containsEntry("NOM", "Amrani").containsEntry("PRENOM", "Sara").containsEntry("NUMERO_ASSURANCE", "ASS-1")
                .containsEntry("DATE_NAISSANCE", java.sql.Date.valueOf("1980-05-06"))
                .containsEntry("DATE_DEBUT_DEMANDE", java.sql.Date.valueOf("2026-01-02"))
                .containsEntry("DATE_FIN_DEMANDE", java.sql.Date.valueOf("2027-01-02")).containsEntry("STATUT", "VALIDEE")
                .containsEntry("DATE_DEBUT_EFFECTIF", java.sql.Date.valueOf("2026-02-03"))
                .containsEntry("DATE_FIN_EFFECTIF", java.sql.Date.valueOf("2026-12-30")).containsEntry("CENTER_NAME", "Centre fictif");
    }

    @Test
    void should_keep_absent_values_null() {
        prisesEnCharge.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, PEC,
                new PriseEnChargeImprimable("Zahi", "Zoe", "", null, null, null, "BROUILLON", null, null));

        assertThat(lignes(parametres(PEC.toString(), null)).getFirst()).containsEntry("DATE_NAISSANCE", null)
                .containsEntry("DATE_DEBUT_DEMANDE", null).containsEntry("DATE_FIN_EFFECTIF", null);
    }

    @Test
    void should_read_the_pec_first_and_the_latest_of_the_patient_otherwise() {
        prisesEnCharge.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, PEC, valeurs("Ancienne", LocalDate.of(2027, 1, 2)));
        prisesEnCharge.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, AUTRE_PEC, valeurs("Recente", LocalDate.of(2027, 6, 2)));

        assertThat(lignes(parametres(null, PATIENT.toString())).getFirst()).containsEntry("NOM", "Recente");
        assertThat(lignes(parametres("", PATIENT.toString())).getFirst()).containsEntry("NOM", "Recente");
        assertThat(lignes(parametres(PEC.toString(), PATIENT.toString())).getFirst()).containsEntry("NOM", "Ancienne");
    }

    @Test
    void should_not_fall_back_to_the_patient_when_the_pec_is_unknown() {
        prisesEnCharge.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, PEC, valeurs("Amrani", LocalDate.of(2027, 1, 2)));

        assertThat(lignes(parametres(UUID.randomUUID().toString(), PATIENT.toString()))).isEmpty();
    }

    @Test
    void should_supply_no_row_without_identifier_or_for_an_unknown_centre() {
        prisesEnCharge.poser(new CentreId(CENTRE), new CentreId(CENTRE), PATIENT, PEC, valeurs("Amrani", LocalDate.of(2027, 1, 2)));

        assertThat(lignes(parametres(null, null))).isEmpty();
        assertThat(lignes(parametres("", ""))).isEmpty();
        nomDuCentre = Optional.empty();
        assertThat(lignes(parametres(PEC.toString(), null))).isEmpty();
    }

    @Test
    void should_fail_like_the_old_cast_for_an_unreadable_identifier_that_is_read() {
        assertThatThrownBy(() -> lignes(parametres("pas-un-uuid", null))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Error executing SQL statement for: prise_en_charge.");
        assertThatThrownBy(() -> lignes(parametres(null, "pas-un-uuid"))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Error executing SQL statement for: prise_en_charge.");
        assertThatThrownBy(() -> lignes(parametres("", "pas-un-uuid"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_read_the_patient_identifier_when_the_pec_is_given() {
        assertThatCode(() -> lignes(parametres(PEC.toString(), "pas-un-uuid"))).doesNotThrowAnyException();
    }

    @Test
    void should_not_guard_the_type_as_before_the_conversion() {
        assertThat(regle.type()).isEqualTo("PEC");
        assertThat(regle.gardeAussiParLeModele()).isFalse();
        assertThatCode(() -> regle.garder(null)).doesNotThrowAnyException();
        assertThatCode(() -> regle.garder(CENTRE)).doesNotThrowAnyException();
    }
}
