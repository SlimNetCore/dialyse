package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.PayeurVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé du compte client de chaque payeur et de la lecture des payeurs du centre : exécuté contre le double en
 * mémoire, les adaptateurs JPA et JDBC sur H2 et sur PostgreSQL 16.
 */
public abstract class PayeurContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996400-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996400-0000-0000-0000-00000000000b"));

    private final PlanDuCentre plan = new PlanDuCentre(new ComptesEnMemoire(), new UsagesEnMemoire());

    protected abstract CompteClientDuPayeurRepository comptesClients();

    protected abstract LectureDesPayeurs lecture();

    /**
     * Un payeur du référentiel (tenu hors de la comptabilité).
     */
    protected abstract PayeurId payeur(CentreId centre, String code, String nom);

    /**
     * Une ligne de compte client écrite directement, sans passer par l'agrégat (donnée ancienne).
     */
    protected abstract void poserLeCompteBrut(CentreId centre, PayeurId payeur, String compte);

    private void affecter(CentreId centre, PayeurId payeur, String compte) {
        comptesClients().enregistrer(CompteClientDuPayeur.affecter(centre, payeur, NumeroDeCompte.de(compte), plan));
    }

    @Test
    void should_list_the_payers_of_the_centre_sorted_by_name_then_code_with_their_account() {
        PayeurId mutuelle = payeur(CENTRE_A, "MUT", "Mutuelle");
        PayeurId cnas = payeur(CENTRE_A, "CNAS", "Caisse nationale");
        payeur(CENTRE_A, "CASNOS", "Caisse nationale");
        payeur(CENTRE_B, "AUTRE", "Autre centre");
        affecter(CENTRE_A, cnas, "411500");
        affecter(CENTRE_A, mutuelle, "411100");

        PageDe<PayeurVue> page = lecture().lister(CENTRE_A, null, 0, 20);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(PayeurVue::code).containsExactly("CASNOS", "CNAS", "MUT");
        assertThat(page.items()).extracting(PayeurVue::compte).containsExactly(null, "411500", "411100");
        assertThat(page.items().get(1).payeurId()).isEqualTo(cnas.valeur());
        assertThat(page.items().get(1).nom()).isEqualTo("Caisse nationale");
    }

    @Test
    void should_page_the_payers_with_the_total_and_return_an_empty_page_beyond_the_end() {
        for (int i = 1; i <= 5; i++) payeur(CENTRE_A, "P" + i, "Payeur " + i);

        assertThat(lecture().lister(CENTRE_A, null, 1, 2).items()).extracting(PayeurVue::code).containsExactly("P3", "P4");
        PageDe<PayeurVue> horsLimites = lecture().lister(CENTRE_A, null, 9, 2);
        assertThat(horsLimites.items()).isEmpty();
        assertThat(horsLimites.total()).isEqualTo(5);
        assertThat(horsLimites.page()).isEqualTo(9);
    }

    @Test
    void should_search_code_and_name_ignoring_case_with_sql_wildcards_KB_CPT_05() {
        payeur(CENTRE_A, "CNAS", "Caisse nationale");
        payeur(CENTRE_A, "MUT", "Mutuelle");

        assertThat(lecture().lister(CENTRE_A, "  cnas ", 0, 20).items()).extracting(PayeurVue::code).containsExactly("CNAS");
        assertThat(lecture().lister(CENTRE_A, "MUTUELLE", 0, 20).items()).extracting(PayeurVue::code).containsExactly("MUT");
        assertThat(lecture().lister(CENTRE_A, "   ", 0, 20).total()).isEqualTo(2);
        assertThat(lecture().lister(CENTRE_A, "zzz", 0, 20).total()).isZero();
        assertThat(lecture().lister(CENTRE_A, "M_T", 0, 20).items()).extracting(PayeurVue::code).containsExactly("MUT");
        assertThat(lecture().lister(CENTRE_A, "%", 0, 20).total()).isEqualTo(2);
    }

    @Test
    void should_find_a_payer_only_in_its_own_centre() {
        PayeurId cnas = payeur(CENTRE_A, "CNAS", "Caisse nationale");

        PayeurVue trouve = lecture().trouver(CENTRE_A, cnas).orElseThrow();

        assertThat(trouve.code()).isEqualTo("CNAS");
        assertThat(trouve.nom()).isEqualTo("Caisse nationale");
        assertThat(trouve.compte()).isNull();
        assertThat(lecture().trouver(CENTRE_B, cnas)).isEmpty();
        assertThat(lecture().trouver(CENTRE_A, new PayeurId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void should_give_the_own_account_of_a_payer_and_replace_it() {
        PayeurId cnas = payeur(CENTRE_A, "CNAS", "Caisse nationale");
        assertThat(lecture().compteDuPayeur(CENTRE_A, cnas)).isEmpty();

        affecter(CENTRE_A, cnas, "411500");
        affecter(CENTRE_A, cnas, "411100");

        assertThat(lecture().compteDuPayeur(CENTRE_A, cnas)).contains("411100");
        assertThat(lecture().compteDuPayeur(CENTRE_B, cnas)).isEmpty();
        assertThat(lecture().lister(CENTRE_A, null, 0, 20).items()).extracting(PayeurVue::compte).containsExactly("411100");
    }

    @Test
    void should_remove_the_account_of_a_payer_so_the_default_account_applies_again() {
        PayeurId cnas = payeur(CENTRE_A, "CNAS", "Caisse nationale");
        affecter(CENTRE_A, cnas, "411500");

        comptesClients().retirer(CENTRE_A, cnas);
        comptesClients().retirer(CENTRE_A, cnas);

        assertThat(lecture().compteDuPayeur(CENTRE_A, cnas)).isEmpty();
    }

    @Test
    void should_remove_the_account_in_its_centre_only() {
        PayeurId cnas = payeur(CENTRE_A, "CNAS", "Caisse nationale");
        affecter(CENTRE_A, cnas, "411500");

        comptesClients().retirer(CENTRE_B, cnas);

        assertThat(lecture().compteDuPayeur(CENTRE_A, cnas)).contains("411500");
    }

    @Test
    void should_return_an_account_outside_the_plan_as_stored_KB_CPT_09() {
        PayeurId cnas = payeur(CENTRE_A, "CNAS", "Caisse nationale");
        poserLeCompteBrut(CENTRE_A, cnas, "999999");

        assertThat(lecture().compteDuPayeur(CENTRE_A, cnas)).contains("999999");
        assertThat(lecture().lister(CENTRE_A, null, 0, 20).items().get(0).compte()).isEqualTo("999999");
    }
}
