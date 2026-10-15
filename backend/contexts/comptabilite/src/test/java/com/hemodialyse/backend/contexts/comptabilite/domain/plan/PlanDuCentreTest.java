package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanDuCentreTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());

    private final ComptesEnMemoire comptes = new ComptesEnMemoire();
    private final UsagesEnMemoire usages = new UsagesEnMemoire();
    private final PlanDuCentre plan = new PlanDuCentre(comptes, usages);

    @Test
    void tant_que_le_centre_n_a_rien_enregistre_son_plan_est_le_plan_de_depart() {
        assertThat(plan.aEnregistreSesComptes(CENTRE)).isFalse();
        assertThat(plan.contientUnCompteActif(CENTRE, NumeroDeCompte.de("512"))).isTrue();
        assertThat(plan.contientUnCompteActif(CENTRE, NumeroDeCompte.de("999"))).isFalse();
    }

    @Test
    void le_plan_de_depart_inclut_les_comptes_du_parametrage() {
        usages.mapping("7061");

        assertThat(plan.planDeDepart(CENTRE).comptes()).hasSize(21);
        assertThat(plan.contientUnCompteActif(CENTRE, NumeroDeCompte.de("7061"))).isTrue();
    }

    @Test
    void la_premiere_modification_enregistre_tout_le_plan_de_depart_comme_celui_du_centre() {
        usages.mapping("7061");

        plan.materialiser(CENTRE);

        assertThat(comptes.nombre(CENTRE)).isEqualTo(21);
        assertThat(comptes.nombre(AUTRE_CENTRE)).isZero();
        assertThat(plan.aEnregistreSesComptes(CENTRE)).isTrue();
    }

    @Test
    void le_plan_deja_enregistre_n_est_pas_materialise_une_seconde_fois() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("1"), "Capital", true));

        plan.materialiser(CENTRE);

        assertThat(comptes.nombre(CENTRE)).isEqualTo(1);
    }

    @Test
    void un_plan_enregistre_ne_connait_plus_que_ses_comptes_actifs() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("1"), "Capital", true));
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("2"), "Ancien", false));

        assertThat(plan.contientUnCompteActif(CENTRE, NumeroDeCompte.de("1"))).isTrue();
        assertThat(plan.contientUnCompteActif(CENTRE, NumeroDeCompte.de("2"))).isFalse();
        assertThat(plan.contientUnCompteActif(CENTRE, NumeroDeCompte.de("512"))).isFalse();
    }

    @Test
    void exiger_un_compte_actif_refuse_un_compte_inconnu_ou_desactive() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("2"), "Ancien", false));

        assertThatThrownBy(() -> plan.exigerCompteActif(CENTRE, NumeroDeCompte.de("2"))).isInstanceOf(CompteInconnu.class)
                .hasMessage("Le compte 2 n'existe pas dans le plan comptable du centre ou est désactivé")
                .extracting("code").isEqualTo("COMPTE_INCONNU");
        assertThatThrownBy(() -> plan.exigerCompteActif(CENTRE, NumeroDeCompte.de("3"))).isInstanceOf(CompteInconnu.class);
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("3"), "Nouveau", true));
        assertThatCode(() -> plan.exigerCompteActif(CENTRE, NumeroDeCompte.de("3"))).doesNotThrowAnyException();
    }
}
