package com.hemodialyse.backend.contexts.planification.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies.SeanceRealiseeAvecPlaceFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction table-driven des séances réalisées fournies vers la semaine du planning (ADR 0098, SE7d), reprise de
 * {@code legacyseance/SeancesRealiseesJdbc} : nom « prénom nom » rogné (une partie nulle compte pour vide), place nulle conservée, risque
 * infectieux, ordre des lignes inchangé, patients à risque lus AVANT les séances.
 */
class SeancesRealiseesPublieesTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0xD8, 1));
    private static final UUID ALICE = new UUID(0xD8, 0x41);
    private static final UUID BORIS = new UUID(0xD8, 0x42);
    private static final UUID SALLE = new UUID(0xD8, 0x11);
    private static final UUID CRENEAU = new UUID(0xD8, 0x21);
    private static final UUID GENERATEUR = new UUID(0xD8, 0x31);
    private static final LocalDate LUNDI = LocalDate.of(2026, 3, 2);

    @ParameterizedTest(name = "prénom [{0}] nom [{1}] donne [{2}]")
    @CsvSource(nullValues = "NULL", delimiter = '|', value = {
            "Alice|Durand|Alice Durand",
            "''|Durand|Durand",
            "'   '|' Martin '|Martin",
            "' Lina'|Ben Ali|Lina Ben Ali",
            "NULL|Durand|Durand",
            "Alice|NULL|Alice",
            "NULL|NULL|''"})
    void le_nom_est_prenom_puis_nom_rogne(String prenom, String nom, String attendu) {
        assertThat(SeancesRealiseesPubliees.nom(prenom, nom)).isEqualTo(attendu);
    }

    @Test
    void la_place_figee_et_le_risque_sont_traduits_et_la_place_nulle_conservee() {
        List<String> ordre = new ArrayList<>();
        var traducteur = new SeancesRealiseesPubliees((centre, du, au) -> {
            ordre.add("seances " + centre + " " + du + " " + au);
            return List.of(new SeanceRealiseeAvecPlaceFournie(ALICE, LUNDI, SALLE, CRENEAU, GENERATEUR, "Durand", "Alice"),
                    new SeanceRealiseeAvecPlaceFournie(BORIS, LUNDI, null, null, null, "Martin", "Boris"),
                    new SeanceRealiseeAvecPlaceFournie(ALICE, LUNDI.minusDays(1), SALLE, null, null, "Durand", "Alice"));
        }, centre -> {
            ordre.add("risques " + centre);
            return Set.of(BORIS);
        });

        List<SeanceRealisee> lues = traducteur.duCentre(CENTRE, LUNDI, LUNDI.plusDays(6));

        assertThat(lues).containsExactly(
                new SeanceRealisee(new PatientId(ALICE), "Alice Durand", LUNDI, new SalleId(SALLE), new CreneauId(CRENEAU),
                        new GenerateurId(GENERATEUR), false),
                new SeanceRealisee(new PatientId(BORIS), "Boris Martin", LUNDI, null, null, null, true),
                new SeanceRealisee(new PatientId(ALICE), "Alice Durand", LUNDI.minusDays(1), new SalleId(SALLE), null, null, false));
        assertThat(ordre).as("risques lus avant les séances, centre et bornes inchangés")
                .containsExactly("risques " + CENTRE.valeur(), "seances " + CENTRE.valeur() + " " + LUNDI + " " + LUNDI.plusDays(6));
    }
}
