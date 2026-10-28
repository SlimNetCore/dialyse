package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.CreerUnCompte.CompteCree;
import com.hemodialyse.backend.contexts.planification.doubles.InfirmiersEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage du référentiel des infirmiers et de leurs comptes, avec les doubles en mémoire (P13, matrice invariant → test) :
 * une unité de travail par cas d'usage, le verrou du référentiel pris en premier, fiche et compte dans la même unité.
 */
class ReferentielDesInfirmiersTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997812-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("99997812-0000-0000-0000-00000000000b"));

    private final InfirmiersEnMemoire monde = new InfirmiersEnMemoire();
    private final FichesDetaillees fiches = new FichesDetaillees(monde.roulementsDesInfirmiers, monde.comptesUtilisateurs);
    private final EnregistrerUnInfirmier enregistrer = new EnregistrerUnInfirmier(monde.unite, monde.verrou, monde.infirmiers,
            monde.identifiants, fiches);
    private final ModifierUnInfirmier modifier = new ModifierUnInfirmier(monde.unite, monde.verrou, monde.infirmiers, fiches);
    private final DesactiverUnInfirmier desactiver = new DesactiverUnInfirmier(monde.unite, monde.verrou, monde.infirmiers,
            monde.comptesUtilisateurs, fiches);
    private final ReactiverUnInfirmier reactiver = new ReactiverUnInfirmier(monde.unite, monde.verrou, monde.infirmiers,
            monde.comptesUtilisateurs, fiches);
    private final LierUnCompte lier = new LierUnCompte(monde.unite, monde.verrou, monde.infirmiers, monde.comptesUtilisateurs, fiches);
    private final CreerUnCompte creer = new CreerUnCompte(monde.unite, monde.verrou, monde.infirmiers, monde.comptesUtilisateurs,
            monde.motsDePasse, fiches);
    private final DelierLeCompte delier = new DelierLeCompte(monde.unite, monde.verrou, monde.infirmiers, monde.comptesUtilisateurs,
            fiches);
    private final ListerLesInfirmiers lister = new ListerLesInfirmiers(monde.infirmiers, fiches);
    private final SuivreLEtatDuCompte suivre = new SuivreLEtatDuCompte(monde.unite, monde.infirmiers);
    private final DelierAvantLaSuppressionDuCompte delierAvantSuppression = new DelierAvantLaSuppressionDuCompte(monde.unite,
            monde.infirmiers);

    private FicheDetaillee enregistrer(CentreId centre, String matricule, String nom) {
        return enregistrer.executer(centre, Matricule.depuisLaSaisie(matricule), new IdentiteDeLInfirmier(nom, "Sara", null),
                Qualification.INFIRMIER, false);
    }

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    /** Fiche liée comme avant la décision « un compte, un seul centre » (données historiques multi-centres). */
    private void lierSansControle(CentreId centre, FicheDetaillee fiche, CompteId compte) {
        monde.infirmiers.enregistrer(monde.infirmiers.pourEcriture(centre, id(fiche)).orElseThrow().lierCompte(compte, true));
    }

    private static InfirmierId id(FicheDetaillee fiche) {
        return new InfirmierId(fiche.id());
    }

    // ------------------------------------------------------------------ fiche

    @Test
    void should_register_a_nurse_under_the_lock_in_one_unit_of_work() {
        FicheDetaillee fiche = enregistrer(CENTRE, "m-001", "Amrani");

        assertThat(fiche.matricule()).isEqualTo("M-001");
        assertThat(fiche.actif()).isTrue();
        assertThat(fiche.compte()).isNull();
        assertThat(monde.verrous).containsExactly(CENTRE);
        assertThat(monde.transactions).isEqualTo(1);
    }

    @Test
    void should_refuse_a_matricule_already_used_in_the_centre_whatever_its_case_but_accept_it_in_another_centre() {
        enregistrer(CENTRE, "M-001", "Amrani");

        refuse(() -> enregistrer(CENTRE, "m-001", "Benali"), "INFIRMIER_MATRICULE_EXISTANT");
        assertThat(enregistrer(AUTRE, "M-001", "Benali").matricule()).isEqualTo("M-001");
    }

    @Test
    void should_modify_a_nurse_and_accept_her_own_matricule_in_another_case() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");

        FicheDetaillee modifiee = modifier.executer(CENTRE, id(fiche), Matricule.depuisLaSaisie("m-001"),
                new IdentiteDeLInfirmier("Benali", null, "0550"), Qualification.MAJOR, true);

        assertThat(modifiee.nom()).isEqualTo("Benali");
        assertThat(modifiee.qualification()).isEqualTo(Qualification.MAJOR);
        assertThat(modifiee.habiliteIsolement()).isTrue();
    }

    @Test
    void should_refuse_to_modify_to_the_matricule_of_another_nurse_or_an_unknown_or_foreign_nurse() {
        enregistrer(CENTRE, "M-001", "Amrani");
        FicheDetaillee deux = enregistrer(CENTRE, "M-002", "Benali");
        FicheDetaillee etrangere = enregistrer(AUTRE, "M-009", "Zerrouki");
        IdentiteDeLInfirmier identite = new IdentiteDeLInfirmier("X", null, null);

        refuse(() -> modifier.executer(CENTRE, id(deux), Matricule.depuisLaSaisie("m-001"), identite, Qualification.MAJOR, false),
                "INFIRMIER_MATRICULE_EXISTANT");
        refuse(() -> modifier.executer(CENTRE, id(etrangere), Matricule.depuisLaSaisie("M-010"), identite, Qualification.MAJOR, false),
                "INFIRMIER_INTROUVABLE");
        refuse(() -> modifier.executer(CENTRE, new InfirmierId(UUID.randomUUID()), Matricule.depuisLaSaisie("M-010"), identite,
                Qualification.MAJOR, false), "INFIRMIER_INTROUVABLE");
    }

    @Test
    void should_list_the_centre_nurses_by_name_with_the_effective_page_size_and_the_account() {
        enregistrer(CENTRE, "M-002", "Benali");
        FicheDetaillee amrani = enregistrer(CENTRE, "M-001", "Amrani");
        enregistrer(AUTRE, "M-001", "Zerrouki");
        CompteId compte = monde.compte("sara", "Compte de Sara", true, CENTRE);
        lier.executer(CENTRE, id(amrani), compte);

        PageDeResultats<FicheDetaillee> page = lister.executer(CENTRE, new Pagination(0, 1000));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.taille()).isEqualTo(100);
        assertThat(page.elements()).extracting(FicheDetaillee::nom).containsExactly("Amrani", "Benali");
        assertThat(page.elements().getFirst().compte().identifiant()).isEqualTo("sara");
        assertThat(lister.executer(CENTRE, new Pagination(0, 0)).elements()).isEmpty();
        assertThat(lister.executer(CENTRE, new Pagination(1, 1)).elements()).extracting(FicheDetaillee::nom).containsExactly("Benali");
    }

    @Test
    void should_take_the_referential_lock_first_and_use_one_unit_of_work_for_every_write() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("sara", "Sara", true, CENTRE);
        monde.verrous.clear();
        monde.transactions = 0;

        modifier.executer(CENTRE, id(fiche), Matricule.depuisLaSaisie("M-001"), new IdentiteDeLInfirmier("Benali", null, null),
                Qualification.MAJOR, false);
        lier.executer(CENTRE, id(fiche), compte);
        desactiver.executer(CENTRE, id(fiche));
        reactiver.executer(CENTRE, id(fiche));
        delier.executer(CENTRE, id(fiche));
        creer.executer(CENTRE, id(fiche), "nouveau.login", null);

        assertThat(monde.verrous).containsOnly(CENTRE).hasSize(6);
        assertThat(monde.transactions).isEqualTo(6);
        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(fiche)).orElseThrow().identite().nom()).isEqualTo("Benali");
        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(fiche)).orElseThrow().compte()).isPresent();
    }

    @Test
    void should_persist_the_state_chosen_by_the_activation_use_cases() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");

        desactiver.executer(CENTRE, id(fiche));
        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(fiche)).orElseThrow().actif()).isFalse();
        reactiver.executer(CENTRE, id(fiche));
        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(fiche)).orElseThrow().actif()).isTrue();
    }

    // ------------------------------------------------------------------ activation et compte

    @Test
    void should_deactivate_the_nurse_and_her_account_in_the_same_unit_of_work_and_reactivate_both() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("sara", "Compte de Sara", true, CENTRE);
        lier.executer(CENTRE, id(fiche), compte);
        int avant = monde.transactions;

        FicheDetaillee inactive = desactiver.executer(CENTRE, id(fiche));

        assertThat(inactive.actif()).isFalse();
        assertThat(monde.comptes.get(compte).actif).isFalse();
        assertThat(monde.transactions).isEqualTo(avant + 1);
        assertThat(reactiver.executer(CENTRE, id(fiche)).actif()).isTrue();
        assertThat(monde.comptes.get(compte).actif).isTrue();
    }

    @Test
    void should_deactivate_a_nurse_whose_account_disappeared_without_error() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("sara", "Compte de Sara", true, CENTRE);
        lier.executer(CENTRE, id(fiche), compte);
        monde.comptes.remove(compte);

        FicheDetaillee inactive = desactiver.executer(CENTRE, id(fiche));

        assertThat(inactive.actif()).isFalse();
        assertThat(inactive.compte()).isNull();
    }

    @Test
    void should_refuse_to_reactivate_an_active_nurse_without_forcing_her_deactivated_account() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("sara", "Compte de Sara", true, CENTRE);
        lier.executer(CENTRE, id(fiche), compte);
        monde.comptes.get(compte).actif = false;

        refuse(() -> reactiver.executer(CENTRE, id(fiche)), "COMPTE_ETAT_INCOHERENT");
        assertThat(monde.comptes.get(compte).actif).isFalse();
    }

    @Test
    void should_never_touch_a_nurse_of_another_centre() {
        FicheDetaillee etrangere = enregistrer(AUTRE, "M-001", "Amrani");

        refuse(() -> desactiver.executer(CENTRE, id(etrangere)), "INFIRMIER_INTROUVABLE");
        refuse(() -> reactiver.executer(CENTRE, id(etrangere)), "INFIRMIER_INTROUVABLE");
        refuse(() -> delier.executer(CENTRE, id(etrangere)), "INFIRMIER_INTROUVABLE");
    }

    @Test
    void should_link_an_existing_account_after_the_checks_in_the_legacy_order() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        FicheDetaillee autre = enregistrer(CENTRE, "M-002", "Benali");
        CompteId compte = monde.compte("sara", "Compte de Sara", true, CENTRE);
        CompteId etranger = monde.compte("etranger", "Autre centre", true, AUTRE);
        CompteId inactif = monde.compte("inactif", "Compte inactif", false, CENTRE);

        refuse(() -> lier.executer(CENTRE, id(fiche), etranger), "COMPTE_INTROUVABLE");
        refuse(() -> lier.executer(CENTRE, id(fiche), new CompteId(UUID.randomUUID())), "COMPTE_INTROUVABLE");
        refuse(() -> lier.executer(CENTRE, id(fiche), inactif), "COMPTE_ETAT_INCOHERENT");
        assertThat(lier.executer(CENTRE, id(fiche), compte).compte().identifiant()).isEqualTo("sara");
        refuse(() -> lier.executer(CENTRE, id(fiche), compte), "COMPTE_DEJA_LIE");
        refuse(() -> lier.executer(CENTRE, id(autre), compte), "COMPTE_DEJA_UTILISE");
    }

    @Test
    void should_refuse_to_link_an_account_already_linked_to_a_nurse_of_another_centre() {
        FicheDetaillee chezA = enregistrer(CENTRE, "M-001", "Amrani");
        FicheDetaillee chezB = enregistrer(AUTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("multi", "Multi", true, CENTRE, AUTRE);
        lier.executer(CENTRE, id(chezA), compte);

        refuse(() -> lier.executer(AUTRE, id(chezB), compte), "COMPTE_LIE_A_UN_AUTRE_CENTRE");
        delier.executer(CENTRE, id(chezA));
        assertThat(lier.executer(AUTRE, id(chezB), compte).compte().identifiant()).isEqualTo("multi");
    }

    @Test
    void should_create_and_link_an_account_in_one_unit_of_work_and_return_the_temporary_password_once() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        int avant = monde.transactions;

        CompteCree cree = creer.executer(CENTRE, id(fiche), "  s.amrani ", AdresseEmail.depuisLaSaisie("sara@centre.dz"));

        assertThat(monde.transactions).isEqualTo(avant + 1);
        assertThat(cree.motDePasseTemporaire()).isEqualTo(monde.motDePasseGenere);
        assertThat(cree.fiche().compte().identifiant()).isEqualTo("s.amrani");
        assertThat(cree.fiche().compte().nomComplet()).isEqualTo("Sara Amrani");
        assertThat(monde.comptes.get(new CompteId(cree.fiche().compte().id().valeur())).email).isEqualTo("sara@centre.dz");
        assertThat(lister.executer(CENTRE, new Pagination(0, 20)).elements().getFirst().compte()).isNotNull();
    }

    @Test
    void should_refuse_to_create_an_account_in_the_legacy_order() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        FicheDetaillee inactive = desactiver.executer(CENTRE, id(enregistrer(CENTRE, "M-002", "Benali")));
        monde.compte("Pris", "Compte pris", true, CENTRE);

        refuse(() -> creer.executer(CENTRE, id(inactive), "valide", null), "COMPTE_ETAT_INCOHERENT");
        refuse(() -> creer.executer(CENTRE, id(fiche), "ab", null), "COMPTE_IDENTIFIANT_INVALIDE");
        refuse(() -> creer.executer(CENTRE, id(fiche), "pris", null), "COMPTE_IDENTIFIANT_EXISTANT");
        refuse(() -> creer.executer(CENTRE, new InfirmierId(UUID.randomUUID()), "valide", null), "INFIRMIER_INTROUVABLE");
        creer.executer(CENTRE, id(fiche), "valide", null);
        refuse(() -> creer.executer(CENTRE, id(fiche), "autre.valide", null), "COMPTE_DEJA_LIE");
        assertThat(monde.comptes).hasSize(2);
    }

    @Test
    void should_unlink_idempotently_and_refuse_an_inactive_nurse_whose_account_is_still_active() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("sara", "Compte de Sara", true, CENTRE);
        lier.executer(CENTRE, id(fiche), compte);

        assertThat(delier.executer(CENTRE, id(fiche)).compte()).isNull();
        assertThat(delier.executer(CENTRE, id(fiche)).compte()).isNull();
        assertThat(monde.comptes).containsKey(compte);
        lier.executer(CENTRE, id(fiche), compte);
        desactiver.executer(CENTRE, id(fiche));
        monde.comptes.get(compte).actif = true;
        refuse(() -> delier.executer(CENTRE, id(fiche)), "COMPTE_DELIAISON_INCOHERENTE");
    }

    @Test
    void should_list_the_linkable_accounts_of_the_centre_not_yet_used_by_a_nurse() {
        FicheDetaillee fiche = enregistrer(CENTRE, "M-001", "Amrani");
        CompteId utilise = monde.compte("a.utilise", "Utilisé", true, CENTRE);
        monde.compte("b.libre", "Libre", true, CENTRE);
        monde.compte("c.etranger", "Étranger", true, AUTRE);
        lier.executer(CENTRE, id(fiche), utilise);

        PageDeResultats<ComptesUtilisateurs.Compte> page = new ListerLesComptesLiables(monde.comptesUtilisateurs)
                .executer(CENTRE, new Pagination(0, 20));

        assertThat(page.elements()).extracting(ComptesUtilisateurs.Compte::identifiant).containsExactly("b.libre");
    }

    // ------------------------------------------------------------------ administration des utilisateurs

    @Test
    void should_make_the_nurses_follow_the_account_only_in_the_centre_of_the_administrator() {
        CompteId compte = monde.compte("multi", "Multi", true, CENTRE, AUTRE);
        FicheDetaillee chezA = enregistrer(CENTRE, "M-001", "Amrani");
        FicheDetaillee chezB = enregistrer(AUTRE, "M-001", "Amrani");
        lier.executer(CENTRE, id(chezA), compte);
        lierSansControle(AUTRE, chezB, compte);

        suivre.executer(compte, false, CENTRE);

        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(chezA)).orElseThrow().actif()).isFalse();
        assertThat(monde.infirmiers.pourEcriture(AUTRE, id(chezB)).orElseThrow().actif()).isTrue();
        suivre.executer(compte, false, null);
        assertThat(monde.infirmiers.pourEcriture(AUTRE, id(chezB)).orElseThrow().actif()).isFalse();
    }

    @Test
    void should_refuse_to_delete_an_account_linked_to_an_active_nurse_and_unlink_inactive_ones() {
        FicheDetaillee active = enregistrer(CENTRE, "M-001", "Amrani");
        FicheDetaillee inactive = enregistrer(AUTRE, "M-001", "Amrani");
        CompteId compte = monde.compte("multi", "Multi", true, CENTRE, AUTRE);
        lier.executer(CENTRE, id(active), compte);
        lierSansControle(AUTRE, inactive, compte);
        desactiver.executer(AUTRE, id(inactive));

        refuse(() -> delierAvantSuppression.executer(compte), "COMPTE_LIE_A_UNE_FICHE_ACTIVE");
        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(active)).orElseThrow().compte()).isPresent();
        assertThat(monde.infirmiers.pourEcriture(AUTRE, id(inactive)).orElseThrow().compte()).isPresent();

        desactiver.executer(CENTRE, id(active));
        delierAvantSuppression.executer(compte);

        assertThat(monde.infirmiers.pourEcriture(CENTRE, id(active)).orElseThrow().compte()).isEmpty();
        assertThat(monde.infirmiers.pourEcriture(AUTRE, id(inactive)).orElseThrow().compte()).isEmpty();
    }
}
