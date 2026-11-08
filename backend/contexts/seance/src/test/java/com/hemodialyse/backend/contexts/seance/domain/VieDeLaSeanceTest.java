package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE2 : validation par l'infirmier, signature du médecin et déverrouillage pour régularisation (matrice des statuts du legacy). */
class VieDeLaSeanceTest {

    private static final SeanceId ID = new SeanceId(new UUID(0, 1));
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-15T09:00:00Z");
    private static final Signataire INFIRMIER = new Signataire("inf-01");
    private static final Signataire MEDECIN = new Signataire("med-01");
    private static final PlaceDeSeance PLACE = new PlaceDeSeance(new UUID(3, 1), new UUID(4, 1), null);

    private static Seance enStatut(StatutDeSeance statut) {
        return Seance.restituer(ID, CENTRE, PATIENT, JOUR, statut, T0, null, null);
    }

    private static Seance validee(Instant le, String par) {
        return Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.VALIDEE, T0, null, new Seance.Jalons(le, le, null, null),
                new Seance.Traces(par, null, null, null));
    }

    @Test
    void should_validate_a_created_session_and_date_the_nurse_signature() {
        var seance = enStatut(StatutDeSeance.CREE);

        boolean change = seance.validerParLInfirmier(T1, INFIRMIER);

        assertThat(change).isTrue();
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(seance.jalons().valideeLe()).isEqualTo(T1);
        assertThat(seance.jalons().signeeParLInfirmierLe()).isEqualTo(T1);
        assertThat(seance.traces().infirmier()).isEqualTo("inf-01");
    }

    @Test
    void should_keep_the_first_validation_when_validated_again() {
        var seance = validee(T0, "inf-initiale");

        boolean change = seance.validerParLInfirmier(T1, INFIRMIER);

        assertThat(change).isFalse();
        assertThat(seance.jalons().valideeLe()).isEqualTo(T0);
        assertThat(seance.traces().infirmier()).isEqualTo("inf-initiale");
    }

    @Test
    void should_accept_validating_a_signed_session_without_changing_it() {
        var seance = enStatut(StatutDeSeance.SIGNEE);
        assertThat(seance.validerParLInfirmier(T1, INFIRMIER)).isFalse();
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.SIGNEE);
    }

    @Test
    void should_refuse_validating_a_billed_session_with_the_billing_code() {
        assertThatThrownBy(() -> enStatut(StatutDeSeance.FACTUREE).validerParLInfirmier(T1, INFIRMIER))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE");
                    assertThat(e.getMessage()).isEqualTo("La seance facturee ne peut plus etre modifiee");
                });
    }

    @Test
    void should_refuse_validating_the_legacy_absent_status() {
        assertThatThrownBy(() -> enStatut(StatutDeSeance.ABSENT).validerParLInfirmier(T1, INFIRMIER))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif()).isEqualTo(MotifDeRefus.STATUT_NON_VALIDABLE);
                    assertThat(e.getMessage()).isEqualTo("La seance n'est pas en statut CREE");
                });
    }

    @Test
    void should_memorize_the_place_once_and_never_replace_it() {
        var seance = enStatut(StatutDeSeance.CREE);
        seance.memoriserPlace(PLACE);
        seance.memoriserPlace(new PlaceDeSeance(new UUID(3, 2), new UUID(4, 2), new UUID(5, 5)));

        assertThat(seance.traces().place()).isEqualTo(PLACE);
    }

    @Test
    void should_sign_a_validated_session_as_the_doctor() {
        var seance = validee(T0, "inf-initiale");

        seance.signerParLeMedecin(T1, MEDECIN);

        assertThat(seance.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(seance.jalons().signeeParLeMedecinLe()).isEqualTo(T1);
        assertThat(seance.traces().medecin()).isEqualTo("med-01");
    }

    @Test
    void should_refuse_signing_before_the_nurse_validation_whatever_the_status() {
        for (var statut : new StatutDeSeance[]{StatutDeSeance.CREE, StatutDeSeance.SIGNEE, StatutDeSeance.ABSENT}) {
            assertThatThrownBy(() -> enStatut(statut).signerParLeMedecin(T1, MEDECIN))
                    .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                        assertThat(e.motif()).isEqualTo(MotifDeRefus.SIGNATURE_AVANT_VALIDATION);
                        assertThat(e.getMessage()).isEqualTo("La signature medecin est autorisee uniquement apres validation infirmiere");
                    });
        }
    }

    @Test
    void should_refuse_signing_a_billed_session() {
        assertThatThrownBy(() -> enStatut(StatutDeSeance.FACTUREE).signerParLeMedecin(T1, MEDECIN))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif()).isEqualTo(MotifDeRefus.SEANCE_FACTUREE));
    }

    @Test
    void should_refuse_a_validated_line_that_already_carries_a_doctor_signature() {
        var incoherente = Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.VALIDEE, T0, null,
                new Seance.Jalons(T0, T0, T0, null), new Seance.Traces("inf", "med-initial", null, null));
        assertThatThrownBy(() -> incoherente.signerParLeMedecin(T1, MEDECIN))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif()).isEqualTo(MotifDeRefus.DEJA_SIGNEE);
                    assertThat(e.getMessage()).isEqualTo("La seance est deja signee par un medecin");
                });
    }

    @Test
    void should_unlock_a_created_session_of_a_past_day_and_keep_status_and_place() {
        var seance = enStatut(StatutDeSeance.CREE);

        boolean change = seance.deverrouillerPourRegularisation(T1, new Signataire("u-admin"), JOUR.plusDays(1));

        assertThat(change).isTrue();
        assertThat(seance.estDeverrouilleePourRegularisation()).isTrue();
        assertThat(seance.jalons().deverrouilleePourRegularisationLe()).isEqualTo(T1);
        assertThat(seance.traces().deverrouilleePar()).isEqualTo("u-admin");
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_keep_the_first_unlock_when_unlocked_again() {
        var seance = Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.CREE, T0, null, new Seance.Jalons(null, null, null, T0),
                new Seance.Traces(null, null, "admin-initial", null));

        boolean change = seance.deverrouillerPourRegularisation(T1, new Signataire("u-admin"), JOUR.plusDays(1));

        assertThat(change).isFalse();
        assertThat(seance.traces().deverrouilleePar()).isEqualTo("admin-initial");
        assertThat(seance.jalons().deverrouilleePourRegularisationLe()).isEqualTo(T0);
    }

    @Test
    void should_refuse_unlocking_today_or_a_future_day_but_not_bound_the_past_to_seven_days() {
        assertThatThrownBy(() -> enStatut(StatutDeSeance.CREE).deverrouillerPourRegularisation(T1, INFIRMIER, JOUR))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif()).isEqualTo(MotifDeRefus.DEVERROUILLAGE_INVALIDE);
                    assertThat(e.getMessage()).isEqualTo("Seule une séance d'un jour passé peut être déverrouillée pour régularisation");
                });
        var tresAncienne = enStatut(StatutDeSeance.CREE);
        assertThat(tresAncienne.deverrouillerPourRegularisation(T1, INFIRMIER, JOUR.plusDays(400))).isTrue();
    }

    @Test
    void should_refuse_unlocking_a_session_that_is_no_longer_created() {
        for (var statut : new StatutDeSeance[]{StatutDeSeance.VALIDEE, StatutDeSeance.SIGNEE, StatutDeSeance.ABSENT}) {
            assertThatThrownBy(() -> enStatut(statut).deverrouillerPourRegularisation(T1, INFIRMIER, JOUR.plusDays(3)))
                    .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                        assertThat(e.motif()).isEqualTo(MotifDeRefus.DEVERROUILLAGE_INVALIDE);
                        assertThat(e.getMessage()).isEqualTo("Seule une séance restée « créée » peut être déverrouillée pour régularisation");
                    });
        }
        assertThatThrownBy(() -> enStatut(StatutDeSeance.FACTUREE).deverrouillerPourRegularisation(T1, INFIRMIER, JOUR.plusDays(3)))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif()).isEqualTo(MotifDeRefus.SEANCE_FACTUREE));
    }

    @Test
    void should_require_the_unlock_before_a_nurse_validates_a_past_day_but_never_before_an_administrator() {
        var passee = enStatut(StatutDeSeance.CREE);
        assertThatThrownBy(() -> passee.exigerLaRegularisation(JOUR.plusDays(1), false))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif()).isEqualTo(MotifDeRefus.REGULARISATION_NON_DEVERROUILLEE);
                    assertThat(e.getMessage()).isEqualTo("Cette séance d'un jour passé doit d'abord être déverrouillée par l'administrateur");
                });
        passee.exigerLaRegularisation(JOUR.plusDays(1), true);
        passee.exigerLaRegularisation(JOUR, false);
        passee.exigerLaRegularisation(JOUR.minusDays(30), false); // KB-SEA-12 : le futur se valide sans limite (reproduit)
        passee.deverrouillerPourRegularisation(T1, INFIRMIER, JOUR.plusDays(1));
        passee.exigerLaRegularisation(JOUR.plusDays(1), false);
    }

    @Test
    void should_bound_the_signatory_to_100_characters_and_default_to_system() {
        assertThat(Signataire.lire(null).valeur()).isEqualTo("system");
        assertThat(Signataire.lire("").valeur()).isEmpty();
        assertThat(Signataire.lire("a".repeat(100)).valeur()).hasSize(100);
        assertThatThrownBy(() -> Signataire.lire("a".repeat(101))).isInstanceOf(SaisieInvalide.class);
    }
}
