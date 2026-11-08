package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceDuJourAmbigue;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScannerUnPatientTest {

    private final Banc banc = new Banc();
    private PatientId patient;

    @BeforeEach
    void patientDuCentre() {
        patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
    }

    private ScannerUnPatient.ResultatDuScan scanner(String qr) {
        return banc.scanner().executer(null, qr, null, null);
    }

    @Test
    void should_create_then_validate_the_session_of_the_day_for_a_nurse() {
        var resultat = scanner("P-001");

        assertThat(resultat.creee()).isTrue();
        assertThat(resultat.validee()).isTrue();
        assertThat(resultat.dejaValidee()).isFalse();
        assertThat(resultat.seance().statut()).isEqualTo(StatutDeSeance.VALIDEE);
        var enregistree = banc.seances.trouver(CENTRE_A, resultat.seance().id()).orElseThrow();
        assertThat(enregistree.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(enregistree.traces().infirmier()).isEqualTo("u-infirmier");
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_CREATED:Nom1", "SEANCE_VALIDATED:Nom1", "SEANCE_VALIDEE:u-infirmier");
    }

    @Test
    void should_serialise_concurrent_scans_of_the_same_patient_and_day_before_reading_the_session_of_the_day() {
        scanner("P-001");
        banc.session.secretaire();
        scanner("P-001");

        assertThat(banc.seances.verrousDuJourDemandes).isEqualTo(2);
        assertThat(banc.seances.toutes()).hasSize(1);
    }

    @Test
    void should_validate_an_existing_created_session_without_creating_another() {
        banc.seances.semer(banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.CREE));

        var resultat = scanner("P-001");

        assertThat(resultat.creee()).isFalse();
        assertThat(resultat.validee()).isTrue();
        assertThat(banc.seances.toutes()).hasSize(1);
    }

    @Test
    void should_return_an_already_validated_session_as_is_without_broadcasting() {
        banc.seances.semer(banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.SIGNEE));

        var resultat = scanner("P-001");

        assertThat(resultat.dejaValidee()).isTrue();
        assertThat(resultat.validee()).isFalse();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_refuse_an_absent_session_for_a_nurse() {
        banc.seances.semer(banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.ABSENT));

        assertThatThrownBy(() -> scanner("P-001")).isInstanceOfSatisfying(SeanceRefusee.class,
                e -> assertThat(e).hasMessage("La séance du jour de ce patient est marquée absente : elle ne peut pas être validée"));
    }

    @Test
    void should_answer_409_when_the_patient_has_several_sessions_the_same_day() {
        banc.seances.semer(banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.CREE));
        banc.seances.semer(banc.ligne(CENTRE_A, patient, 2, StatutDeSeance.CREE));

        assertThatThrownBy(() -> scanner("P-001")).isInstanceOf(SeanceDuJourAmbigue.class);
        banc.session.secretaire();
        assertThatThrownBy(() -> scanner("P-001")).isInstanceOf(SeanceDuJourAmbigue.class);
    }

    @Test
    void should_create_without_validating_for_a_secretary_and_keep_the_created_flag_false() {
        banc.session.secretaire();

        var resultat = scanner("P-001");

        assertThat(resultat.creee()).isFalse();
        assertThat(resultat.validee()).isFalse();
        assertThat(resultat.dejaValidee()).isFalse();
        assertThat(resultat.seance().statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_CREATED:Nom1", "SEANCE_CREEE:u-secretaire");
    }

    @Test
    void should_broadcast_again_for_a_secretary_while_the_session_is_only_created() {
        banc.session.secretaire();
        scanner("P-001");
        banc.diffusionsRecues.clear();

        scanner("P-001");

        assertThat(banc.seances.toutes()).hasSize(1);
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_CREATED:Nom1", "SEANCE_CREEE:u-secretaire");
    }

    @Test
    void should_return_an_absent_session_as_already_validated_for_a_secretary() {
        banc.session.secretaire();
        banc.seances.semer(banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.ABSENT));

        assertThat(scanner("P-001").dejaValidee()).isTrue();
    }

    @Test
    void should_refuse_a_patient_outside_the_planning_without_a_derogation() {
        banc.situations.put(Banc.cle(CENTRE_A, patient), new SituationDuPlanning(false, false, false, null, null, null));

        assertThatThrownBy(() -> scanner("P-001")).isInstanceOfSatisfying(SeanceRefusee.class,
                e -> assertThat(e.motif().code()).isEqualTo("SEANCE_HORS_PLANNING_JOUR"));
        assertThat(banc.seances.toutes()).isEmpty();
        banc.session.secretaire();
        assertThatThrownBy(() -> scanner("P-001")).isInstanceOf(SeanceRefusee.class);
    }

    @Test
    void should_record_the_derogation_of_a_session_outside_the_planning() {
        banc.situations.put(Banc.cle(CENTRE_A, patient), new SituationDuPlanning(false, false, false, null, null, null));

        var resultat = banc.scanner().executer(null, "P-001", "rattrapage", " Rattrapage apres un voyage ");

        var derogation = resultat.seance().derogation().orElseThrow();
        assertThat(derogation.motif()).isEqualTo(MotifHorsPlanning.RATTRAPAGE);
        assertThat(derogation.precision()).isEqualTo("Rattrapage apres un voyage");
        assertThat(derogation.administrateur()).isFalse();
    }

    @Test
    void should_let_only_an_administrator_cross_a_closure_day() {
        banc.situations.put(Banc.cle(CENTRE_A, patient), new SituationDuPlanning(true, true, false, null, null, null));

        assertThatThrownBy(() -> banc.scanner().executer(null, "P-001", "URGENCE", null))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_CENTRE_FERME"));

        banc.session.administrateur();
        var resultat = banc.scanner().executer(null, "P-001", "URGENCE", "Urgence un jour ferme");
        assertThat(resultat.seance().horsPlanning()).isTrue();
        assertThat(resultat.seance().derogation().orElseThrow().administrateur()).isTrue();
    }

    @Test
    void should_refuse_an_unknown_motif_before_looking_for_the_patient() {
        assertThatThrownBy(() -> banc.scanner().executer(null, "INCONNU", "VACANCES", null))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e).hasMessage("Motif de séance hors planning inconnu : VACANCES"));
    }

    @Test
    void should_check_the_planning_before_the_billing() {
        banc.situations.put(Banc.cle(CENTRE_A, patient), new SituationDuPlanning(false, false, false, null, null, null));
        banc.causes.put(Banc.cle(CENTRE_A, patient), CauseDeNonFacturation.PRISE_EN_CHARGE);

        assertThatThrownBy(() -> scanner("P-001")).isInstanceOfSatisfying(SeanceRefusee.class,
                e -> assertThat(e.motif().code()).isEqualTo("SEANCE_HORS_PLANNING_JOUR"));
        assertThatThrownBy(() -> banc.scanner().executer(null, "P-001", "URGENCE", null)).isInstanceOfSatisfying(SeanceRefusee.class,
                e -> assertThat(e.motif().code()).isEqualTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void should_undo_the_creation_when_the_validation_fails() {
        banc.laPlaceEchoue = true;

        assertThatThrownBy(() -> scanner("P-001")).isInstanceOf(IllegalStateException.class);

        assertThat(banc.seances.toutes()).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_use_the_day_of_the_centre_for_the_session_of_the_day() {
        banc.horlogeA("2026-06-14T23:30:00Z");

        var resultat = scanner("P-001");

        assertThat(resultat.seance().jour()).hasToString("2026-06-15");
    }

    @Test
    void should_refuse_a_qr_of_another_centre_as_an_unknown_patient() {
        var autre = banc.patient(CENTRE_B, 2, "P-002", "B2");

        assertThatThrownBy(() -> scanner(autre.valeur().toString())).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> scanner("P-002")).isInstanceOf(PatientIntrouvable.class);
        assertThat(banc.seances.toutes()).isEmpty();
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_one_of_the_session_before_reading_the_qr() {
        assertThatThrownBy(() -> banc.scanner().executer(CENTRE_B.valeur(), null, null, null)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> banc.scanner().executer(CENTRE_A.valeur(), null, null, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_a_qr_of_punctuation_only_instead_of_picking_an_arbitrary_patient() {
        assertThatThrownBy(() -> scanner("-")).isInstanceOf(SaisieInvalide.class);
        assertThat(banc.seances.toutes()).isEmpty();
    }
}
