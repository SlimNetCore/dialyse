package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer.PriseEnChargeImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.contrat.ListeDesPrisesEnChargeAImprimerEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** DOC5g (ADR 0199) : la règle d'impression pose les lignes de la liste des prises en charge, champ par champ, sans garde. */
class RegleDImpressionDeLaListeDesPrisesEnChargeTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000f5a72");

    private final ListeDesPrisesEnChargeAImprimerEnMemoire prisesEnCharge = new ListeDesPrisesEnChargeAImprimerEnMemoire();
    private final RegleDImpressionDeLaListeDesPrisesEnCharge regle = new RegleDImpressionDeLaListeDesPrisesEnCharge(prisesEnCharge);

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lignes() {
        Map<String, Object> donnees = regle.donneesDuServeur(CENTRE, Map.of("CENTER_ID", "ignore"));
        assertThat(donnees).containsOnlyKeys("LIGNES");
        return (List<Map<String, Object>>) donnees.get("LIGNES");
    }

    @Test
    void should_supply_the_rows_with_the_template_field_names_and_types() {
        prisesEnCharge.poser(new CentreId(CENTRE), new PriseEnChargeImprimee("P-1", "Amrani", "Sara", "ASS-1", LocalDate.of(2026, 1, 2),
                LocalDate.of(2027, 1, 2), "VALIDEE"));

        assertThat(lignes()).hasSize(1);
        assertThat(lignes().getFirst()).containsOnlyKeys("CODE_PATIENT", "NOM", "PRENOM", "NUMERO_ASSURANCE", "DATE_DEBUT_DEMANDE",
                        "DATE_FIN_DEMANDE", "STATUT")
                .containsEntry("CODE_PATIENT", "P-1").containsEntry("NOM", "Amrani").containsEntry("PRENOM", "Sara")
                .containsEntry("NUMERO_ASSURANCE", "ASS-1").containsEntry("DATE_DEBUT_DEMANDE", java.sql.Date.valueOf("2026-01-02"))
                .containsEntry("DATE_FIN_DEMANDE", java.sql.Date.valueOf("2027-01-02")).containsEntry("STATUT", "VALIDEE");
    }

    @Test
    void should_keep_absent_values_null() {
        prisesEnCharge.poser(new CentreId(CENTRE), new PriseEnChargeImprimee(null, "Zahi", "Zoe", "ASS-9", null, null, "EN_ATTENTE"));

        assertThat(lignes().getFirst()).containsEntry("CODE_PATIENT", null).containsEntry("DATE_DEBUT_DEMANDE", null)
                .containsEntry("DATE_FIN_DEMANDE", null);
    }

    @Test
    void should_supply_an_empty_list_for_a_centre_without_coverage_request() {
        assertThat(lignes()).isEmpty();
    }

    @Test
    void should_not_guard_the_type_as_before_the_conversion() {
        assertThat(regle.type()).isEqualTo("LISTE_PEC");
        assertThat(regle.gardeAussiParLeModele()).isFalse();
        assertThatCode(() -> regle.garder(null)).doesNotThrowAnyException();
        assertThatCode(() -> regle.garder(CENTRE)).doesNotThrowAnyException();
    }
}
