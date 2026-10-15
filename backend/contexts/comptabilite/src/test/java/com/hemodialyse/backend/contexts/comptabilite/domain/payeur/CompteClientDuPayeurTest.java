package com.hemodialyse.backend.contexts.comptabilite.domain.payeur;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteInconnu;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompteClientDuPayeurTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final PayeurId PAYEUR = new PayeurId(UUID.randomUUID());

    private final ComptesEnMemoire comptes = new ComptesEnMemoire();
    private final PlanDuCentre plan = new PlanDuCentre(comptes, new UsagesEnMemoire());

    @Test
    void un_payeur_recoit_un_compte_actif_du_plan_de_depart() {
        CompteClientDuPayeur compteClient = CompteClientDuPayeur.affecter(CENTRE, PAYEUR, NumeroDeCompte.de("411500"), plan);

        assertThat(compteClient.centre()).isEqualTo(CENTRE);
        assertThat(compteClient.payeur()).isEqualTo(PAYEUR);
        assertThat(compteClient.compte()).isEqualTo(NumeroDeCompte.de("411500"));
    }

    @Test
    void un_compte_hors_plan_est_refuse() {
        assertThatThrownBy(() -> CompteClientDuPayeur.affecter(CENTRE, PAYEUR, NumeroDeCompte.de("999"), plan))
                .isInstanceOf(CompteInconnu.class);
    }

    @Test
    void un_compte_desactive_du_plan_enregistre_est_refuse() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("4119"), "Ancien", false));

        assertThatThrownBy(() -> CompteClientDuPayeur.affecter(CENTRE, PAYEUR, NumeroDeCompte.de("4119"), plan))
                .isInstanceOf(CompteInconnu.class);
    }

    @Test
    void un_payeur_exige_une_identite() {
        assertThatThrownBy(() -> new PayeurId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_centre_exige_une_identite() {
        assertThatThrownBy(() -> new CentreId(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Centre requis");
    }
}
