package com.hemodialyse.backend.contexts.comptabilite.application.payeur;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurIntrouvable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteInconnu;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompteInvalide;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.PayeursEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComptesPayeursCasDUsageTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());

    private final ComptesEnMemoire comptes = new ComptesEnMemoire();
    private final PayeursEnMemoire payeurs = new PayeursEnMemoire();
    private final UniteDeTravailDirecte unite = new UniteDeTravailDirecte();
    private final PlanDuCentre plan = new PlanDuCentre(comptes, new UsagesEnMemoire());
    private final ListerLesPayeurs lister = new ListerLesPayeurs(payeurs, unite);
    private final AffecterUnCompteAuPayeur affecter = new AffecterUnCompteAuPayeur(payeurs, payeurs, plan, unite);
    private final ConsulterLeCompteDuPayeur consulter = new ConsulterLeCompteDuPayeur(payeurs);

    private final PayeurId cnas = payeurs.ajouterPayeur(CENTRE, "CNAS", "Caisse nationale");
    private final PayeurId casnos = payeurs.ajouterPayeur(CENTRE, "CASNOS", "Caisse des non salariés");
    private final PayeurId autreCentre = payeurs.ajouterPayeur(AUTRE_CENTRE, "MUT", "Mutuelle");

    @Test
    void les_payeurs_du_centre_sont_listes_par_nom_avec_leur_compte_ou_null() {
        affecter.executer(CENTRE, casnos, "411500");

        PageDe<PayeurVue> page = lister.executer(CENTRE, null, 0, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(PayeurVue::code).containsExactly("CASNOS", "CNAS");
        assertThat(page.items()).extracting(PayeurVue::compte).containsExactly("411500", null);
    }

    @Test
    void la_recherche_porte_sur_le_code_et_le_nom_et_la_taille_est_bornee_de_1_a_100() {
        assertThat(lister.executer(CENTRE, "casnos", 0, 20).items()).extracting(PayeurVue::code).containsExactly("CASNOS");
        assertThat(lister.executer(CENTRE, "NATIONALE", 0, 20).items()).extracting(PayeurVue::code).containsExactly("CNAS");
        assertThat(lister.executer(CENTRE, null, 0, 0).items()).hasSize(1);
        assertThat(lister.executer(CENTRE, null, 0, 5000).size()).isEqualTo(100);
        assertThat(lister.executer(CENTRE, null, -2, 20).page()).isZero();
    }

    @Test
    void affecter_un_compte_actif_du_plan_l_enregistre_normalise_en_majuscules() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("abc"), "Perso", true));

        PayeurVue vue = affecter.executer(CENTRE, cnas, " abc ");

        assertThat(vue).isEqualTo(new PayeurVue(cnas.valeur(), "CNAS", "Caisse nationale", "ABC"));
        assertThat(consulter.compteDuPayeur(CENTRE.valeur(), cnas.valeur())).contains("ABC");
    }

    @Test
    void remplacer_le_compte_d_un_payeur_et_partager_un_compte_entre_payeurs_est_permis() {
        affecter.executer(CENTRE, cnas, "411500");
        affecter.executer(CENTRE, cnas, "411100");
        affecter.executer(CENTRE, casnos, "411100");

        assertThat(consulter.compteDuPayeur(CENTRE.valeur(), cnas.valeur())).contains("411100");
        assertThat(consulter.compteDuPayeur(CENTRE.valeur(), casnos.valeur())).contains("411100");
    }

    @Test
    void un_compte_vide_blanc_ou_absent_retire_le_compte_et_c_est_idempotent() {
        affecter.executer(CENTRE, cnas, "411500");

        for (String retrait : new String[]{"", "   ", null}) {
            PayeurVue vue = affecter.executer(CENTRE, cnas, retrait);
            assertThat(vue.compte()).isNull();
            assertThat(vue.code()).isEqualTo("CNAS");
        }
        assertThat(consulter.compteDuPayeur(CENTRE.valeur(), cnas.valeur())).isEmpty();
    }

    @Test
    void un_compte_hors_plan_est_refuse() {
        assertThatThrownBy(() -> affecter.executer(CENTRE, cnas, "999")).isInstanceOf(CompteInconnu.class);
    }

    @Test
    void un_compte_desactive_du_plan_enregistre_est_refuse() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("4119"), "Ancien", false));

        assertThatThrownBy(() -> affecter.executer(CENTRE, cnas, "4119")).isInstanceOf(CompteInconnu.class);
    }

    @Test
    void un_compte_mal_forme_est_refuse() {
        assertThatThrownBy(() -> affecter.executer(CENTRE, cnas, "41-1")).isInstanceOf(NumeroDeCompteInvalide.class);
    }

    @Test
    void un_payeur_inconnu_ou_d_un_autre_centre_est_refuse_avant_toute_validation_du_compte() {
        assertThatThrownBy(() -> affecter.executer(CENTRE, autreCentre, "41-1")).isInstanceOf(PayeurIntrouvable.class)
                .hasMessage("Payeur introuvable dans ce centre");
        assertThatThrownBy(() -> affecter.executer(CENTRE, new PayeurId(UUID.randomUUID()), "999"))
                .isInstanceOf(PayeurIntrouvable.class);
        assertThatThrownBy(() -> affecter.executer(CENTRE, autreCentre, "")).isInstanceOf(PayeurIntrouvable.class);
    }

    @Test
    void un_compte_hors_plan_en_base_est_liste_et_publie_tel_quel_KB_CPT_09() {
        payeurs.poserLeCompteBrut(CENTRE, cnas, "999999");

        assertThat(lister.executer(CENTRE, "CNAS", 0, 20).items().get(0).compte()).isEqualTo("999999");
        assertThat(consulter.compteDuPayeur(CENTRE.valeur(), cnas.valeur())).contains("999999");
    }

    @Test
    void un_payeur_sans_compte_propre_n_a_pas_de_compte_publie() {
        assertThat(consulter.compteDuPayeur(CENTRE.valeur(), cnas.valeur())).isEmpty();
        assertThat(consulter.compteDuPayeur(AUTRE_CENTRE.valeur(), cnas.valeur())).isEmpty();
    }
}
