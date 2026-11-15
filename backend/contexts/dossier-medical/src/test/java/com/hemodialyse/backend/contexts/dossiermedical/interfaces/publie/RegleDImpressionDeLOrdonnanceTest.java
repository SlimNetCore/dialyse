package com.hemodialyse.backend.contexts.dossiermedical.interfaces.publie;

import com.hemodialyse.backend.contexts.dossiermedical.application.ComposerLesLignesDeLOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.GarderLImpressionDeLOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.IdentitesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer.LigneImprimable;
import com.hemodialyse.backend.contexts.dossiermedical.application.LigneDeLOrdonnanceImprimee;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.OrdonnancesAImprimerEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DOC5d (ADR 0196) : la règle d'impression pose les lignes de l'état ; traducteur table par table ; identifiants absents ou illisibles. */
class RegleDImpressionDeLOrdonnanceTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000d5a01");
    private static final UUID ORDONNANCE = UUID.fromString("00000000-0000-0000-0000-0000000d5a03");

    private final OrdonnancesAImprimerEnMemoire ordonnances = new OrdonnancesAImprimerEnMemoire();

    private RegleDImpressionDeLOrdonnance regle() {
        var garde = new GarderLImpressionDeLOrdonnance(() -> Set.of("ROLE_MEDECIN"), demande -> new CentreId(CENTRE));
        var lignes = new ComposerLesLignesDeLOrdonnance(ordonnances,
                (c, p) -> Optional.of(new IdentitesDesPatients.Identite("Dupré", "Zoé", "F", LocalDate.of(1961, 3, 7), "ASS-1")),
                c -> Optional.of("Centre fictif"));
        return new RegleDImpressionDeLOrdonnance(garde, lignes);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lignes(Map<String, Object> donnees) {
        assertThat(donnees).containsOnlyKeys("LIGNES");
        return (List<Map<String, Object>>) donnees.get("LIGNES");
    }

    @Test
    void should_supply_the_rows_with_the_template_field_names_and_types() {
        ordonnances.poser(new CentreId(CENTRE), ORDONNANCE, UUID.randomUUID(), "ORD-00001", LocalDate.of(2026, 1, 4), "dr fictif",
                List.of(new LigneImprimable("Code", "Nom", "1 par jour", "orale", 30, 2, "à jeun")));

        var lignes = lignes(regle().donneesDuServeur(CENTRE, Map.of("ordonnanceId", ORDONNANCE.toString())));

        assertThat(lignes).hasSize(1);
        assertThat(lignes.getFirst()).containsOnlyKeys("NOM", "PRENOM", "DATE_NAISSANCE", "NUMERO_ASSURANCE", "CENTER_NAME", "NUMERO",
                "DATE_PRESCRIPTION", "MEDECIN_ID", "MEDICAMENT_LIBELLE", "POSOLOGIE", "VOIE", "DUREE_JOURS", "QUANTITE", "INSTRUCTIONS");
        assertThat(lignes.getFirst()).containsEntry("NOM", "Dupré").containsEntry("PRENOM", "Zoé")
                .containsEntry("DATE_NAISSANCE", java.sql.Date.valueOf("1961-03-07")).containsEntry("NUMERO_ASSURANCE", "ASS-1")
                .containsEntry("CENTER_NAME", "Centre fictif").containsEntry("NUMERO", "ORD-00001")
                .containsEntry("DATE_PRESCRIPTION", java.sql.Date.valueOf("2026-01-04")).containsEntry("MEDECIN_ID", "dr fictif")
                .containsEntry("MEDICAMENT_LIBELLE", "Code").containsEntry("POSOLOGIE", "1 par jour").containsEntry("VOIE", "orale")
                .containsEntry("DUREE_JOURS", 30).containsEntry("QUANTITE", 2).containsEntry("INSTRUCTIONS", "à jeun");
    }

    @Test
    void should_keep_absent_values_null() {
        var champs = ChampsDeLOrdonnanceImprimee.champs(new LigneDeLOrdonnanceImprimee("N", "P", null, null, "C", null, LocalDate.of(2026, 1, 4),
                null, null, "1", null, null, null, null));

        assertThat(champs).containsEntry("DATE_NAISSANCE", null).containsEntry("NUMERO", null).containsEntry("MEDECIN_ID", null)
                .containsEntry("MEDICAMENT_LIBELLE", null).containsEntry("DUREE_JOURS", null).containsEntry("QUANTITE", null);
    }

    @Test
    void should_supply_no_row_when_the_identifier_is_absent_or_unknown() {
        assertThat(lignes(regle().donneesDuServeur(CENTRE, Map.of()))).isEmpty();
        assertThat(lignes(regle().donneesDuServeur(CENTRE, Map.of("ordonnanceId", UUID.randomUUID().toString())))).isEmpty();
    }

    @Test
    void should_fail_the_print_with_the_historical_text_when_the_identifier_is_unreadable() {
        assertThatThrownBy(() -> regle().donneesDuServeur(CENTRE, Map.of("ordonnanceId", "pas-un-uuid")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Error executing SQL statement for: ordonnance.");
        assertThatThrownBy(() -> regle().donneesDuServeur(CENTRE, Map.of("ordonnanceId", "")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Error executing SQL statement for: ordonnance.");
    }
}
