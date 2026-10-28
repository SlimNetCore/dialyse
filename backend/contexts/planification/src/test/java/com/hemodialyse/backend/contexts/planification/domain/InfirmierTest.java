package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants de la fiche d'un infirmier (RG-INF-001 à 012, matrice invariant → test de P13).
 */
class InfirmierTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997811-0000-0000-0000-00000000000a"));
    private static final CompteId COMPTE = new CompteId(UUID.fromString("99997811-0000-0000-0000-0000000000c1"));

    private static Infirmier fiche() {
        return Infirmier.enregistrer(new InfirmierId(UUID.randomUUID()), CENTRE, Matricule.depuisLaSaisie("m-001"),
                new IdentiteDeLInfirmier("Amrani", "Sara", null), Qualification.MAJOR, true);
    }

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    // ------------------------------------------------------------------ saisie

    @Test
    void should_normalise_the_matricule_to_upper_case_and_trim_it() {
        assertThat(Matricule.depuisLaSaisie("  m-001 ").valeur()).isEqualTo("M-001");
    }

    @Test
    void should_keep_a_stored_matricule_as_it_was_written() {
        assertThat(Matricule.enregistre("m-001").valeur()).isEqualTo("m-001");
        assertThat(Matricule.enregistre("m-001").memeQue(Matricule.depuisLaSaisie("M-001"))).isTrue();
    }

    @Test
    void should_refuse_a_missing_blank_or_too_long_matricule() {
        assertThatThrownBy(() -> Matricule.depuisLaSaisie(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Matricule.depuisLaSaisie("   ")).isInstanceOf(SaisieInvalide.class);
        assertThat(Matricule.depuisLaSaisie("x".repeat(50)).valeur()).hasSize(50);
        assertThatThrownBy(() -> Matricule.depuisLaSaisie("x".repeat(51))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_require_a_name_trim_the_texts_and_turn_blank_first_name_and_phone_into_absent() {
        IdentiteDeLInfirmier identite = new IdentiteDeLInfirmier("  Amrani ", "   ", " ");

        assertThat(identite.nom()).isEqualTo("Amrani");
        assertThat(identite.prenom()).isNull();
        assertThat(identite.telephone()).isNull();
        assertThat(new IdentiteDeLInfirmier("Amrani", "Sara", null).nomComplet()).isEqualTo("Sara Amrani");
        assertThat(identite.nomComplet()).isEqualTo("Amrani");
        assertThatThrownBy(() -> new IdentiteDeLInfirmier(" ", null, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_accept_the_exact_column_bounds_and_refuse_beyond() {
        assertThat(new IdentiteDeLInfirmier("n".repeat(255), "p".repeat(255), "0".repeat(50)).nom()).hasSize(255);
        assertThatThrownBy(() -> new IdentiteDeLInfirmier("n".repeat(256), null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new IdentiteDeLInfirmier("n", "p".repeat(256), null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new IdentiteDeLInfirmier("n", null, "0".repeat(51))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_an_unknown_or_missing_qualification() {
        assertThat(Qualification.depuisLaSaisie(" AIDE_SOIGNANT ")).isEqualTo(Qualification.AIDE_SOIGNANT);
        assertThatThrownBy(() -> Qualification.depuisLaSaisie(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Qualification.depuisLaSaisie("infirmier")).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_accept_only_a_well_formed_email_within_the_column_length() {
        assertThat(AdresseEmail.depuisLaSaisie("  sara@centre.dz ").valeur()).isEqualTo("sara@centre.dz");
        assertThat(AdresseEmail.depuisLaSaisie("   ")).isNull();
        assertThat(AdresseEmail.depuisLaSaisie(null)).isNull();
        assertThatThrownBy(() -> AdresseEmail.depuisLaSaisie("pas-un-email")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> AdresseEmail.depuisLaSaisie("a b@c.dz")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> AdresseEmail.depuisLaSaisie("a".repeat(141) + "@centre.dz")).isInstanceOf(SaisieInvalide.class);
        assertThat(AdresseEmail.depuisLaSaisie("a".repeat(140) + "@centre.dz").valeur()).hasSize(150);
    }

    @Test
    void should_accept_a_login_of_three_to_fifty_allowed_characters_after_trimming() {
        assertThat(IdentifiantDeConnexion.depuisLaSaisie("  s.amrani_1-x ").valeur()).isEqualTo("s.amrani_1-x");
        assertThat(IdentifiantDeConnexion.depuisLaSaisie("a".repeat(50)).valeur()).hasSize(50);
        for (String invalide : new String[]{"ab", "a".repeat(51), "s amrani", "sara@dz", "", "sâra"}) {
            refuse(() -> IdentifiantDeConnexion.depuisLaSaisie(invalide), "COMPTE_IDENTIFIANT_INVALIDE");
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    @Test
    void should_register_an_active_nurse_without_account() {
        Infirmier fiche = fiche();

        assertThat(fiche.actif()).isTrue();
        assertThat(fiche.compte()).isEmpty();
        assertThat(fiche.matricule().valeur()).isEqualTo("M-001");
    }

    @Test
    void should_replace_the_data_but_keep_the_state_and_the_account_even_when_inactive() {
        Infirmier liee = fiche().lierCompte(COMPTE, true).desactiver();

        Infirmier modifiee = liee.modifier(Matricule.depuisLaSaisie("m-002"), new IdentiteDeLInfirmier("Benali", null, "0550"),
                Qualification.INFIRMIER, false);

        assertThat(modifiee.id()).isEqualTo(liee.id());
        assertThat(modifiee.matricule().valeur()).isEqualTo("M-002");
        assertThat(modifiee.qualification()).isEqualTo(Qualification.INFIRMIER);
        assertThat(modifiee.habiliteIsolement()).isFalse();
        assertThat(modifiee.actif()).isFalse();
        assertThat(modifiee.compte()).contains(COMPTE);
    }

    @Test
    void should_deactivate_and_reactivate_idempotently() {
        Infirmier inactive = fiche().desactiver();

        assertThat(inactive.desactiver().actif()).isFalse();
        assertThat(inactive.reactiver(EtatDuCompte.ABSENT).actif()).isTrue();
        assertThat(inactive.reactiver(EtatDuCompte.ABSENT).reactiver(EtatDuCompte.ABSENT).actif()).isTrue();
    }

    @Test
    void should_refuse_to_reactivate_an_active_nurse_whose_account_is_deactivated() {
        Infirmier liee = fiche().lierCompte(COMPTE, true);

        refuse(() -> liee.reactiver(EtatDuCompte.INACTIF), "COMPTE_ETAT_INCOHERENT");
        assertThat(liee.reactiver(EtatDuCompte.ACTIF).actif()).isTrue();
        assertThat(liee.desactiver().reactiver(EtatDuCompte.INACTIF).actif()).isTrue();
    }

    // ------------------------------------------------------------------ compte

    @Test
    void should_link_an_account_only_once_and_in_the_same_state_as_the_nurse() {
        Infirmier liee = fiche().lierCompte(COMPTE, true);

        assertThat(liee.compte()).contains(COMPTE);
        refuse(() -> liee.lierCompte(new CompteId(UUID.randomUUID()), true), "COMPTE_DEJA_LIE");
        refuse(() -> fiche().lierCompte(COMPTE, false), "COMPTE_ETAT_INCOHERENT");
        refuse(() -> fiche().desactiver().lierCompte(COMPTE, true), "COMPTE_ETAT_INCOHERENT");
        assertThat(fiche().desactiver().lierCompte(COMPTE, false).compte()).contains(COMPTE);
    }

    @Test
    void should_refuse_an_account_created_for_an_inactive_or_already_linked_nurse() {
        refuse(() -> fiche().desactiver().exigerActivePourUnNouveauCompte(), "COMPTE_ETAT_INCOHERENT");
        refuse(() -> fiche().desactiver().lierLeCompteCree(COMPTE), "COMPTE_ETAT_INCOHERENT");
        refuse(() -> fiche().lierCompte(COMPTE, true).exigerSansCompte(), "COMPTE_DEJA_LIE");
        assertThat(fiche().lierLeCompteCree(COMPTE).compte()).contains(COMPTE);
    }

    @Test
    void should_unlink_idempotently_but_refuse_an_inactive_nurse_whose_account_stays_active() {
        Infirmier liee = fiche().lierCompte(COMPTE, true);

        assertThat(liee.delierCompte(EtatDuCompte.ACTIF).compte()).isEmpty();
        assertThat(fiche().delierCompte(EtatDuCompte.ABSENT).compte()).isEmpty();
        assertThat(liee.desactiver().delierCompte(EtatDuCompte.INACTIF).compte()).isEmpty();
        assertThat(liee.desactiver().delierCompte(EtatDuCompte.ABSENT).compte()).isEmpty();
        refuse(() -> liee.desactiver().delierCompte(EtatDuCompte.ACTIF), "COMPTE_DELIAISON_INCOHERENTE");
    }

    @Test
    void should_refuse_to_unlink_for_the_deletion_of_an_account_while_the_nurse_is_active() {
        Infirmier liee = fiche().lierCompte(COMPTE, true);

        refuse(liee::delierAvantLaSuppressionDuCompte, "COMPTE_LIE_A_UNE_FICHE_ACTIVE");
        assertThat(liee.desactiver().delierAvantLaSuppressionDuCompte().compte()).isEmpty();
    }

    @Test
    void should_follow_the_state_of_the_account_changed_in_the_user_administration() {
        Infirmier liee = fiche().lierCompte(COMPTE, true);

        assertThat(liee.suivreLeCompte(false).actif()).isFalse();
        assertThat(liee.suivreLeCompte(false).suivreLeCompte(true).actif()).isTrue();
        assertThat(liee.suivreLeCompte(true)).isSameAs(liee);
    }
}
