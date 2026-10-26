package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.IdentifiantDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du référentiel des infirmiers et des comptes : exécuté contre le double en mémoire, l'adaptateur JDBC sur H2 et le même
 * adaptateur sur PostgreSQL 16 (InfirmiersJdbcContrat). Le double passe le même jeu de tests que l'adaptateur réel.
 */
public abstract class InfirmiersContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997813-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99997813-0000-0000-0000-00000000000b"));

    protected abstract InfirmiersRepository infirmiers();

    protected abstract ComptesUtilisateurs comptes();

    /** Pose un compte INFIRMIER rattaché aux centres donnés (Identité et accès). */
    protected abstract CompteId unCompte(String identifiant, String nomComplet, boolean actif, CentreId... centres);

    protected Infirmier fiche(CentreId centre, String matricule, String nom, String prenom) {
        return Infirmier.enregistrer(new InfirmierId(UUID.randomUUID()), centre, Matricule.depuisLaSaisie(matricule),
                new IdentiteDeLInfirmier(nom, prenom, "0550 11 22 33"), Qualification.MAJOR, true);
    }

    // ------------------------------------------------------------------ fiches

    @Test
    void should_read_back_what_was_registered_and_hide_it_from_another_centre() {
        Infirmier fiche = fiche(CENTRE, "m-001", "Amrani", "Sara");

        infirmiers().enregistrer(fiche);

        Infirmier lue = infirmiers().pourEcriture(CENTRE, fiche.id()).orElseThrow();
        assertThat(lue.matricule().valeur()).isEqualTo("M-001");
        assertThat(lue.identite()).isEqualTo(fiche.identite());
        assertThat(lue.qualification()).isEqualTo(Qualification.MAJOR);
        assertThat(lue.habiliteIsolement()).isTrue();
        assertThat(lue.actif()).isTrue();
        assertThat(lue.compte()).isEmpty();
        assertThat(infirmiers().pourEcriture(AUTRE, fiche.id())).isEmpty();
        assertThat(infirmiers().pourEcriture(CENTRE, new InfirmierId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void should_replace_the_whole_fiche_when_registered_again() {
        Infirmier fiche = fiche(CENTRE, "M-001", "Amrani", "Sara");
        infirmiers().enregistrer(fiche);
        CompteId compte = unCompte("sara", "Sara", true, CENTRE);

        infirmiers().enregistrer(fiche.modifier(Matricule.depuisLaSaisie("M-002"), new IdentiteDeLInfirmier("Benali", null, null),
                Qualification.INFIRMIER, false).lierCompte(compte, true).desactiver());

        Infirmier lue = infirmiers().pourEcriture(CENTRE, fiche.id()).orElseThrow();
        assertThat(lue.matricule().valeur()).isEqualTo("M-002");
        assertThat(lue.identite().nom()).isEqualTo("Benali");
        assertThat(lue.identite().prenom()).isNull();
        assertThat(lue.identite().telephone()).isNull();
        assertThat(lue.qualification()).isEqualTo(Qualification.INFIRMIER);
        assertThat(lue.habiliteIsolement()).isFalse();
        assertThat(lue.actif()).isFalse();
        assertThat(lue.compte()).contains(compte);
        assertThat(infirmiers().page(CENTRE, new Pagination(0, 20)).total()).isEqualTo(1);
    }

    @Test
    void should_find_a_matricule_whatever_its_case_within_the_centre_only() {
        Infirmier fiche = fiche(CENTRE, "M-001", "Amrani", "Sara");
        infirmiers().enregistrer(fiche);

        assertThat(infirmiers().parMatricule(CENTRE, Matricule.enregistre("m-001"))).map(Infirmier::id).contains(fiche.id());
        assertThat(infirmiers().parMatricule(CENTRE, Matricule.depuisLaSaisie("M-001"))).isPresent();
        assertThat(infirmiers().parMatricule(CENTRE, Matricule.depuisLaSaisie("M-002"))).isEmpty();
        assertThat(infirmiers().parMatricule(AUTRE, Matricule.depuisLaSaisie("M-001"))).isEmpty();
    }

    @Test
    void should_find_the_nurses_of_an_account_in_one_centre_or_in_all() {
        CompteId compte = unCompte("multi", "Multi", true, CENTRE, AUTRE);
        Infirmier chezA = fiche(CENTRE, "M-001", "Amrani", "Sara").lierCompte(compte, true);
        Infirmier chezB = fiche(AUTRE, "M-001", "Amrani", "Sara").lierCompte(compte, true);
        infirmiers().enregistrer(chezA);
        infirmiers().enregistrer(chezB);
        infirmiers().enregistrer(fiche(CENTRE, "M-002", "Benali", "Karim"));

        assertThat(infirmiers().parCompte(CENTRE, compte)).map(Infirmier::id).contains(chezA.id());
        assertThat(infirmiers().fichesDuCompteDuCentre(AUTRE, compte)).extracting(Infirmier::id).containsExactly(chezB.id());
        assertThat(infirmiers().fichesDuCompte(compte)).extracting(Infirmier::id).containsExactlyInAnyOrder(chezA.id(), chezB.id());
        assertThat(infirmiers().fichesDuCompte(new CompteId(UUID.randomUUID()))).isEmpty();
        assertThat(infirmiers().parCompte(CENTRE, new CompteId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void should_page_the_centre_nurses_by_name_first_name_then_matricule_with_the_total() {
        infirmiers().enregistrer(fiche(CENTRE, "M-003", "Benali", "Karim"));
        infirmiers().enregistrer(fiche(CENTRE, "M-002", "Amrani", "Zahra"));
        infirmiers().enregistrer(fiche(CENTRE, "M-001", "Amrani", "Sara"));
        infirmiers().enregistrer(fiche(AUTRE, "M-001", "Autre", "Centre"));

        PageDeResultats<Infirmier> premiere = infirmiers().page(CENTRE, new Pagination(0, 2));
        PageDeResultats<Infirmier> seconde = infirmiers().page(CENTRE, new Pagination(1, 2));

        assertThat(premiere.total()).isEqualTo(3);
        assertThat(premiere.taille()).isEqualTo(2);
        assertThat(premiere.elements()).extracting(f -> f.matricule().valeur()).containsExactly("M-001", "M-002");
        assertThat(seconde.elements()).extracting(f -> f.matricule().valeur()).containsExactly("M-003");
        assertThat(infirmiers().page(CENTRE, new Pagination(5, 2)).elements()).isEmpty();
        PageDeResultats<Infirmier> vide = infirmiers().page(CENTRE, new Pagination(0, 0));
        assertThat(vide.elements()).isEmpty();
        assertThat(vide.total()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ comptes

    @Test
    void should_create_an_active_nurse_account_attached_to_the_centre_and_find_it() {
        Compte cree = comptes().creerCompteInfirmier(CENTRE, IdentifiantDeConnexion.depuisLaSaisie("s.amrani"),
                "Mot2passe!Temp", "Sara Amrani", AdresseEmail.depuisLaSaisie("sara@centre.dz"));

        assertThat(cree.actif()).isTrue();
        assertThat(cree.nomComplet()).isEqualTo("Sara Amrani");
        Compte lu = comptes().compteInfirmierDuCentre(CENTRE, cree.id()).orElseThrow();
        assertThat(lu.identifiant()).isEqualTo("s.amrani");
        assertThat(comptes().compteInfirmierDuCentre(AUTRE, cree.id())).isEmpty();
        assertThat(comptes().parIds(java.util.List.of(cree.id(), new CompteId(UUID.randomUUID())))).extracting(Compte::id)
                .containsExactly(cree.id());
        assertThat(comptes().parIds(java.util.List.of())).isEmpty();
    }

    @Test
    void should_tell_that_a_login_exists_without_regard_to_the_case_in_any_centre() {
        unCompte("Existant.P2", "Existant", true, AUTRE);

        assertThat(comptes().identifiantExiste(IdentifiantDeConnexion.depuisLaSaisie("existant.p2"))).isTrue();
        assertThat(comptes().identifiantExiste(IdentifiantDeConnexion.depuisLaSaisie("EXISTANT.P2"))).isTrue();
        assertThat(comptes().identifiantExiste(IdentifiantDeConnexion.depuisLaSaisie("autre.p2"))).isFalse();
    }

    @Test
    void should_change_the_active_state_of_an_account_and_ignore_a_missing_one() {
        CompteId compte = unCompte("sara", "Sara", true, CENTRE);

        comptes().definirActif(compte, false);
        assertThat(comptes().parIds(java.util.List.of(compte)).getFirst().actif()).isFalse();
        comptes().definirActif(compte, true);
        assertThat(comptes().parIds(java.util.List.of(compte)).getFirst().actif()).isTrue();
        comptes().definirActif(new CompteId(UUID.randomUUID()), true);
    }

    @Test
    void should_list_the_linkable_accounts_of_the_centre_by_login_without_those_already_used() {
        CompteId utilise = unCompte("a.utilise", "Utilisé", true, CENTRE);
        unCompte("b.libre", "Libre", true, CENTRE);
        unCompte("c.inactif", "Inactif", false, CENTRE);
        unCompte("d.etranger", "Étranger", true, AUTRE);
        infirmiers().enregistrer(fiche(CENTRE, "M-001", "Amrani", "Sara").lierCompte(utilise, true));
        infirmiers().enregistrer(fiche(AUTRE, "M-001", "Amrani", "Sara"));

        PageDeResultats<Compte> page = comptes().comptesLiables(CENTRE, new Pagination(0, 1));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.elements()).extracting(Compte::identifiant).containsExactly("b.libre");
        assertThat(comptes().comptesLiables(CENTRE, new Pagination(1, 1)).elements()).extracting(Compte::identifiant)
                .containsExactly("c.inactif");
        assertThat(comptes().comptesLiables(AUTRE, new Pagination(0, 20)).elements()).extracting(Compte::identifiant)
                .containsExactly("d.etranger");
    }
}
