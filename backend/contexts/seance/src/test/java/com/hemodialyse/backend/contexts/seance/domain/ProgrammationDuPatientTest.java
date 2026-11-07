package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgrammationDuPatientTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    private static SituationDuPlanning attendu() {
        return new SituationDuPlanning(false, true, false, null, null, null);
    }

    private static DerogationHorsPlanning derogation(boolean administrateur) {
        return new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, null, administrateur);
    }

    private static void refus(SituationDuPlanning situation, DerogationHorsPlanning derogation, String code, String message) {
        assertThatThrownBy(() -> ProgrammationDuPatient.exiger(situation, JOUR, derogation))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo(code);
                    assertThat(e).hasMessage(message);
                });
    }

    @Test
    void should_not_ask_for_a_derogation_when_the_patient_is_expected() {
        assertThat(ProgrammationDuPatient.exiger(attendu(), JOUR, null)).isEmpty();
        assertThat(ProgrammationDuPatient.exiger(attendu(), JOUR, derogation(true))).isEmpty();
    }

    @Test
    void should_refuse_a_day_that_is_not_a_dialysis_day_without_a_derogation() {
        refus(new SituationDuPlanning(false, false, false, null, null, null), null, "SEANCE_HORS_PLANNING_JOUR",
                "Ce patient n'est pas programmé aujourd'hui (ce n'est pas un de ses jours de dialyse)");
    }

    @Test
    void should_return_the_derogation_for_a_day_that_is_not_a_dialysis_day() {
        var derogation = derogation(false);
        assertThat(ProgrammationDuPatient.exiger(new SituationDuPlanning(false, false, false, null, null, null), JOUR, derogation))
                .contains(derogation);
    }

    @Test
    void should_refuse_a_patient_asleep_or_not_yet_admitted_or_gone() {
        String message = "Ce patient n'est pas attendu aujourd'hui (sommeil, sortie ou séjour terminé)";
        refus(new SituationDuPlanning(false, true, true, null, null, null), null, "SEANCE_HORS_PLANNING_PATIENT", message);
        refus(new SituationDuPlanning(false, true, false, null, null, JOUR.plusDays(1)), null, "SEANCE_HORS_PLANNING_PATIENT", message);
        refus(new SituationDuPlanning(false, true, false, "DECEDE", JOUR, null), null, "SEANCE_HORS_PLANNING_PATIENT", message);
    }

    @Test
    void should_expect_a_patient_admitted_the_same_day() {
        assertThat(ProgrammationDuPatient.exiger(new SituationDuPlanning(false, true, false, null, null, JOUR), JOUR, null)).isEmpty();
    }

    @Test
    void should_expect_a_transferred_patient_up_to_the_departure_day_included() {
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "TRANSFERE", JOUR, null), JOUR)).isEmpty();
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "GUERRI", JOUR.minusDays(1), null), JOUR))
                .contains(ProgrammationDuPatient.RaisonHorsPlanning.PATIENT_NON_ATTENDU);
    }

    @Test
    void should_expect_a_deceased_or_grafted_patient_only_before_the_event_day() {
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "GREFFE", JOUR.plusDays(1), null), JOUR)).isEmpty();
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "GREFFE", JOUR, null), JOUR))
                .contains(ProgrammationDuPatient.RaisonHorsPlanning.PATIENT_NON_ATTENDU);
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "DECEDE", null, null), JOUR))
                .contains(ProgrammationDuPatient.RaisonHorsPlanning.PATIENT_NON_ATTENDU);
    }

    @Test
    void should_expect_an_occasional_patient_until_the_end_of_the_stay_included() {
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "OCCASIONNEL", JOUR, null), JOUR)).isEmpty();
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "OCCASIONNEL", null, null), JOUR)).isEmpty();
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "VACANCIER_LOCAL", JOUR.minusDays(1), null), JOUR))
                .contains(ProgrammationDuPatient.RaisonHorsPlanning.PATIENT_NON_ATTENDU);
    }

    @Test
    void should_expect_a_patient_with_an_unknown_state() {
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(false, true, false, "AUTRE_ETAT", null, null), JOUR)).isEmpty();
    }

    @Test
    void should_let_only_an_administrator_cross_a_closure_day() {
        var ferme = new SituationDuPlanning(true, true, false, null, null, null);
        String message = "Le centre est fermé ce jour : seul un administrateur peut enregistrer une séance";
        refus(ferme, null, "SEANCE_CENTRE_FERME", message);
        refus(ferme, derogation(false), "SEANCE_CENTRE_FERME", message);
        var administrateur = derogation(true);
        assertThat(ProgrammationDuPatient.exiger(ferme, JOUR, administrateur)).isEqualTo(Optional.of(administrateur));
    }

    @Test
    void should_report_the_closure_before_the_dialysis_day() {
        assertThat(ProgrammationDuPatient.evaluer(new SituationDuPlanning(true, false, true, null, null, null), JOUR))
                .contains(ProgrammationDuPatient.RaisonHorsPlanning.CENTRE_FERME);
    }
}
