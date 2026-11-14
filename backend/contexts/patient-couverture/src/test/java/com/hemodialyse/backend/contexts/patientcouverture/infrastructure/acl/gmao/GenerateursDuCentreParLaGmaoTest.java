package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.patientcouverture.application.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.contrat.EquipementsEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction de la description GMAO en générateur du centre (LEC-PAT-GMAO, ADR 0181) : code, fabricant et statut repris tels quels ;
 * équipement supprimé décrit (la GMAO le rend), d'un autre centre ou inconnu : absent.
 */
class GenerateursDuCentreParLaGmaoTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("0c0c0c0c-0000-4000-8000-00000000c001"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("0c0c0c0c-0000-4000-8000-00000000c002"));
    private static final UUID G1 = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000001");
    private static final UUID SUPPRIME = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000002");

    private final GenerateursDuCentre generateurs = new GenerateursDuCentreParLaGmao(new EquipementsEnMemoire()
            .equipement(CENTRE.valeur(), G1, "G-01", "Fresenius", "EN_MAINTENANCE")
            .equipement(CENTRE.valeur(), SUPPRIME, "G-SUP", "Braun", "REFORME"));

    @Test
    void should_traduire_la_description_en_generateur_du_centre() {
        assertThat(generateurs.trouver(CENTRE, G1)).contains(new GenerateursDuCentre.Generateur(G1, "G-01", "Fresenius", "EN_MAINTENANCE"));
    }

    @Test
    void should_decrire_encore_un_equipement_supprime() {
        assertThat(generateurs.trouver(CENTRE, SUPPRIME)).hasValueSatisfying(g -> assertThat(g.code()).isEqualTo("G-SUP"));
    }

    @Test
    void should_ne_pas_decrire_un_equipement_d_un_autre_centre_ni_inconnu() {
        assertThat(generateurs.trouver(AUTRE, G1)).isEmpty();
        assertThat(generateurs.trouver(CENTRE, UUID.randomUUID())).isEmpty();
    }
}
