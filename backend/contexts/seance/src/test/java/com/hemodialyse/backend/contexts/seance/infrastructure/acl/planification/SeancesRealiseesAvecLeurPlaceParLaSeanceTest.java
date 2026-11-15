package com.hemodialyse.backend.contexts.seance.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies.SeanceRealiseeAvecPlaceFournie;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees.SeanceRealiseeAvecPlacePubliee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fournisseur de la Planification (ADR 0098, SE7d) : délégation pure, chaque champ recopié (place nulle conservée, nom et prénom BRUTS :
 * la recomposition reste à la Planification), ordre publié conservé, centre et bornes transmis sans changement.
 */
class SeancesRealiseesAvecLeurPlaceParLaSeanceTest {

    private static final UUID CENTRE = new UUID(0xDB, 1);
    private static final UUID ALICE = new UUID(0xDB, 0x41);
    private static final UUID BORIS = new UUID(0xDB, 0x42);
    private static final UUID SALLE = new UUID(0xDB, 0x11);
    private static final UUID CRENEAU = new UUID(0xDB, 0x21);
    private static final UUID GENERATEUR = new UUID(0xDB, 0x31);
    private static final LocalDate LUNDI = LocalDate.of(2026, 3, 2);

    @Test
    void chaque_champ_est_recopie_et_l_ordre_conserve() {
        var fournisseur = new SeancesRealiseesAvecLeurPlaceParLaSeance((centre, du, au) -> centre.equals(CENTRE) && du.equals(LUNDI)
                && au.equals(LUNDI.plusDays(6))
                ? List.of(new SeanceRealiseeAvecPlacePubliee(BORIS, LUNDI.plusDays(1), null, null, null, " Martin ", "   "),
                new SeanceRealiseeAvecPlacePubliee(ALICE, LUNDI, SALLE, CRENEAU, GENERATEUR, "Durand", "Alice"))
                : List.of());

        assertThat(fournisseur.duCentre(CENTRE, LUNDI, LUNDI.plusDays(6))).containsExactly(
                new SeanceRealiseeAvecPlaceFournie(BORIS, LUNDI.plusDays(1), null, null, null, " Martin ", "   "),
                new SeanceRealiseeAvecPlaceFournie(ALICE, LUNDI, SALLE, CRENEAU, GENERATEUR, "Durand", "Alice"));
        assertThat(fournisseur.duCentre(new UUID(0xDB, 2), LUNDI, LUNDI.plusDays(6))).isEmpty();
    }
}
