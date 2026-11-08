package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE2 : signature du médecin et déverrouillage pour régularisation, ports en mémoire. */
class SignerEtDeverrouillerUneSeanceTest {

    private Banc banc;
    private PatientId patient;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
    }

    private Seance semer(int n, StatutDeSeance statut) {
        var s = banc.ligne(CENTRE_A, patient, n, statut);
        banc.seances.semer(s);
        return s;
    }

    private Seance relue(Seance s) {
        return banc.seances.trouver(CENTRE_A, s.id()).orElseThrow();
    }

    @Test
    void should_sign_a_validated_session_as_the_signatory_of_the_body_without_any_broadcast() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        var signee = banc.signer().executer(CENTRE_A.valeur(), s.id().valeur(), "med-01");

        assertThat(signee.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        var lue = relue(s);
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(lue.traces().medecin()).isEqualTo("med-01");
        assertThat(lue.jalons().signeeParLeMedecinLe()).isEqualTo(MAINTENANT);
        assertThat(banc.diffusionsRecues).isEmpty();
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
    }

    @Test
    void should_sign_whatever_the_day_of_the_session() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        banc.horlogeA("2027-06-15T10:00:00Z");
        banc.signer().executer(CENTRE_A.valeur(), s.id().valeur(), "med-01");
        assertThat(relue(s).statut()).isEqualTo(StatutDeSeance.SIGNEE);
    }

    @Test
    void should_refuse_a_second_signature_and_a_signature_before_validation() {
        var validee = semer(1, StatutDeSeance.VALIDEE);
        banc.signer().executer(CENTRE_A.valeur(), validee.id().valeur(), "med-01");
        assertThatThrownBy(() -> banc.signer().executer(CENTRE_A.valeur(), validee.id().valeur(), "med-02"))
                .isInstanceOf(SeanceRefusee.class);
        assertThat(relue(validee).traces().medecin()).isEqualTo("med-01");

        var creee = semer(2, StatutDeSeance.CREE);
        assertThatThrownBy(() -> banc.signer().executer(CENTRE_A.valeur(), creee.id().valeur(), "med-01"))
                .isInstanceOfSatisfying(SeanceRefusee.class,
                        e -> assertThat(e.getMessage()).isEqualTo("La signature medecin est autorisee uniquement apres validation infirmiere"));
    }

    @Test
    void should_refuse_a_signatory_of_more_than_100_characters_an_unknown_session_and_another_centre() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        assertThatThrownBy(() -> banc.signer().executer(CENTRE_A.valeur(), s.id().valeur(), "m".repeat(101)))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(relue(s).statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThatThrownBy(() -> banc.signer().executer(CENTRE_A.valeur(), new UUID(8, 8), "m")).isInstanceOf(SeanceIntrouvable.class);
        assertThatThrownBy(() -> banc.signer().executer(CENTRE_B.valeur(), s.id().valeur(), "m")).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_unlock_a_created_session_of_a_past_day_as_the_account_of_the_session_and_broadcast() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.horlogeA("2026-06-20T10:00:00Z");
        banc.session.administrateur();

        var deverrouillee = banc.deverrouiller().executer(CENTRE_A.valeur(), s.id().valeur());

        assertThat(deverrouillee.estDeverrouilleePourRegularisation()).isTrue();
        var lue = relue(s);
        assertThat(lue.traces().deverrouilleePar()).isEqualTo("u-admin");
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_DEVERROUILLEE:Nom1");
    }

    @Test
    void should_broadcast_again_when_unlocked_again_without_changing_the_first_unlock() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.horlogeA("2026-06-20T10:00:00Z");
        banc.session.administrateur();
        banc.deverrouiller().executer(CENTRE_A.valeur(), s.id().valeur());
        banc.session.utilisateur = "u-autre-admin";
        banc.horlogeA("2026-06-21T10:00:00Z");

        banc.deverrouiller().executer(CENTRE_A.valeur(), s.id().valeur());

        assertThat(relue(s).traces().deverrouilleePar()).isEqualTo("u-admin");
        assertThat(banc.diffusionsRecues).hasSize(2);
    }

    @Test
    void should_use_the_day_of_the_centre_to_decide_that_a_day_is_past() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.session.administrateur();
        banc.horlogeA("2026-06-15T22:30:00Z"); // 23h30 à Alger : le 15 n'est pas passé
        assertThatThrownBy(() -> banc.deverrouiller().executer(CENTRE_A.valeur(), s.id().valeur())).isInstanceOf(SeanceRefusee.class);
        banc.horlogeA("2026-06-15T23:30:00Z"); // 00h30 le 16
        banc.deverrouiller().executer(CENTRE_A.valeur(), s.id().valeur());
        assertThat(relue(s).estDeverrouilleePourRegularisation()).isTrue();
    }

    @Test
    void should_refuse_unlocking_what_is_not_created_and_leave_everything_untouched() {
        var validee = semer(1, StatutDeSeance.VALIDEE);
        banc.horlogeA("2026-06-20T10:00:00Z");
        assertThatThrownBy(() -> banc.deverrouiller().executer(CENTRE_A.valeur(), validee.id().valeur())).isInstanceOf(SeanceRefusee.class);
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_undo_the_unlock_when_the_patient_has_disappeared() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.horlogeA("2026-06-20T10:00:00Z");
        banc.patients.fiches.clear();
        assertThatThrownBy(() -> banc.deverrouiller().executer(CENTRE_A.valeur(), s.id().valeur())).isInstanceOf(PatientIntrouvable.class);
        assertThat(relue(s).estDeverrouilleePourRegularisation()).isFalse();
    }
}
