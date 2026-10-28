package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.doubles.MondeDOptimisation;
import com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P23 / RG-PLN-100 : la nuit enchaîne COUVERTURE, MAINTENANCE puis PATIENTS (30 s par phase, SYSTEME), signale ce qui mérite examen,
 * n'applique jamais, envoie toujours un bilan (RIEN, PROPOSITIONS, ECHEC, REPORTEE). Centres actifs seulement, jour du centre.
 */
class ReplanificationDeNuitApplicationTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 27);
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 10, 25);

    private final MondeDOptimisation m = new MondeDOptimisation();
    private final OptimisationEnMemoire opt = m.optimisation;

    @BeforeEach
    void centre() {
        opt.donneesDuCentre = new JeuDOptimisation().patient(1, LMV, false, poste(A, MATIN, A1)).infirmierActif(1, false).donnees();
        m.places.aujourdhui = AUJOURDHUI;
    }

    private void repondre(int rang, ResultatDOptimisation resultat) {
        opt.demandes.get(rang).ecouteur().termine(resultat, "s");
    }

    private static ResultatDOptimisation rien() {
        return OptimisationEnMemoire.resultatVide();
    }

    // ── centres ──

    @Test
    void should_replan_the_centres_that_enabled_the_option_regardless_of_the_active_flag() {
        CentreId inactif = new CentreId(UUID.randomUUID());
        CentreId sansOption = new CentreId(UUID.randomUUID());
        m.parametrage.reglages.enregistrer(CENTRE, new ReglagesDePlanification(true, 5, 40, 1));
        m.parametrage.reglages.enregistrer(inactif, new ReglagesDePlanification(true, 5, 40, 1));
        m.parametrage.reglages.enregistrer(sansOption, new ReglagesDePlanification(false, 5, 40, 1));

        assertThat(m.nuit.centres()).containsExactlyInAnyOrder(CENTRE, inactif);
    }

    @Test
    void should_replan_each_selected_centre_at_its_own_day_and_survive_one_in_error() {
        m.parametrage.reglages.enregistrer(CENTRE, new ReglagesDePlanification(true, 5, 40, 1));

        m.nuit.executer();

        assertThat(opt.demandes).hasSize(1);
        assertThat(opt.demandes.getFirst().parametres().debutSemaine()).isEqualTo(DIMANCHE);
    }

    // ── enchaînement ──

    @Test
    void should_chain_coverage_maintenance_then_patients_each_starting_when_the_previous_one_ends() {
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        assertThat(opt.demandes).hasSize(1);
        ParametresDOptimisation couverture = opt.demandes.get(0).parametres();
        assertThat(couverture.perimetre()).isEqualTo(Perimetre.COUVERTURE);
        assertThat(couverture.nbSemaines()).isEqualTo(2);
        assertThat(couverture.dureeMaxSecondes()).isEqualTo(30);
        assertThat(couverture.debutSemaine()).isEqualTo(DIMANCHE);
        assertThat(opt.executions.values().iterator().next().lancePar()).isEqualTo("SYSTEME");

        repondre(0, rien());
        assertThat(opt.demandes).hasSize(2);
        assertThat(opt.demandes.get(1).parametres().perimetre()).isEqualTo(Perimetre.MAINTENANCE);
        assertThat(opt.demandes.get(1).parametres().nbSemaines()).isEqualTo(2);

        repondre(1, rien());
        assertThat(opt.demandes).hasSize(3);
        ParametresDOptimisation patients = opt.demandes.get(2).parametres();
        assertThat(patients.perimetre()).isEqualTo(Perimetre.PATIENTS);
        assertThat(patients.debutSemaine()).isEqualTo(DIMANCHE.plusWeeks(1));
        assertThat(patients.nbSemaines()).isEqualTo(2);
        assertThat(opt.notifications).isEmpty();
    }

    @Test
    void should_always_send_a_nothing_to_report_balance_and_never_apply_anything() {
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        repondre(0, rien());
        repondre(1, rien());
        repondre(2, rien());

        assertThat(opt.notifications).containsExactly("BILAN|RIEN|0|0");
        assertThat(opt.executions.values()).allMatch(e -> e.appliqueeLe() == null);
        assertThat(m.remplacementsPoses).isEmpty();
        assertThat(opt.temporaires).isEmpty();
    }

    @Test
    void should_signal_a_proposition_worth_examining_with_its_value_and_count_it_in_the_balance() {
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        repondre(0, new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(),
                List.of(new VacationPlanifiee(AUJOURDHUI, JourDeLaSemaine.MARDI, A, MATIN, infirmier(1), "N", false)),
                List.of(new VacationNonPourvue(AUJOURDHUI, JourDeLaSemaine.MARDI, A, MATIN, 2)), null, null));
        repondre(1, rien());
        repondre(2, new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Indicateurs(0, 0, 10, 0, 3, 0, 0, 0, 0), new Indicateurs(0, 0, 8, 0, 1, 0, 0, 0, 0)));

        assertThat(opt.notifications).containsExactly("PROPOSITION|COUVERTURE|SOUS_EFFECTIF|3", "PROPOSITION|PATIENTS|GAIN|4",
                "BILAN|PROPOSITIONS|2|0");
    }

    @Test
    void should_signal_a_maintenance_proposition_with_the_moves_and_the_sessions_without_solution() {
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        repondre(0, rien());
        repondre(1, new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of(),
                List.of(new ResultatDOptimisation.SeanceSansSolution(JeuDOptimisationPatients.p1(), "P", AUJOURDHUI,
                        JourDeLaSemaine.MARDI, null, "x"))));
        repondre(2, rien());

        assertThat(opt.notifications).containsExactly("PROPOSITION|MAINTENANCE|MAINTENANCE|1", "BILAN|PROPOSITIONS|1|0");
    }

    @Test
    void should_report_a_failed_calculation_in_the_balance_and_still_run_the_other_steps() {
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        repondre(0, rien());
        opt.demandes.get(1).ecouteur().echec("boom");
        assertThat(opt.demandes).hasSize(3);
        repondre(2, rien());

        assertThat(opt.notifications).containsExactly("BILAN|ECHEC|0|1");
    }

    @Test
    void should_count_the_three_steps_as_failures_when_the_data_cannot_be_read_and_notify_once() {
        opt.donneesDuCentre = null;

        m.nuit.replanifier(CENTRE, AUJOURDHUI);

        assertThat(opt.demandes).isEmpty();
        assertThat(opt.executions).isEmpty();
        assertThat(opt.notifications).containsExactly("BILAN|ECHEC|0|3");
    }

    @Test
    void should_postpone_the_night_when_a_calculation_is_already_in_progress() {
        ExecutionDOptimisation manuelle = m.lancer.executer(CENTRE, JeuDOptimisation.parametres(Perimetre.PATIENTS), "admin");

        m.nuit.replanifier(CENTRE, AUJOURDHUI);

        assertThat(opt.demandes).hasSize(1);
        assertThat(opt.executions.get(manuelle.id()).enCours()).isTrue();
        assertThat(opt.notifications).containsExactly("BILAN|REPORTEE|0|0");
    }

    @Test
    void should_postpone_the_night_when_the_centre_lock_is_held_elsewhere() {
        m.places.verrouRefuse = true;
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        assertThat(opt.notifications).containsExactly("BILAN|REPORTEE|0|0");
    }

    @Test
    void should_postpone_after_a_first_step_when_a_manual_calculation_starts_in_between() {
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        // pendant la couverture, un calcul est lancé à la main : on simule en le posant après la fin de la première étape
        opt.executions.put(opt.demandes.getFirst().execution(), opt.executions.get(opt.demandes.getFirst().execution())
                .terminer(rien(), "s", MondeDOptimisation.MAINTENANT));
        ExecutionDOptimisation manuelle = m.lancer.executer(CENTRE, JeuDOptimisation.parametres(Perimetre.PATIENTS), "admin");
        assertThat(manuelle.enCours()).isTrue();

        opt.demandes.getFirst().ecouteur().termine(rien(), "s");

        assertThat(opt.notifications).containsExactly("BILAN|REPORTEE|0|0");
    }

    // ── horizon de la couverture ──

    private void absenceJusquau(LocalDate fin) {
        m.personnelDeLaPresence = new Personnel(List.of(), List.of(),
                List.of(new AbsenceConnue(infirmier(1).valeur(), AUJOURDHUI.minusDays(1), fin, TypeAbsence.CONGE)), List.of(),
                new RatioDeLaPresence(4, false));
    }

    @Test
    void should_cover_at_least_two_weeks_when_nobody_is_absent() {
        assertThat(m.nuit.semainesDeCouverture(CENTRE, AUJOURDHUI)).isEqualTo(2);
    }

    @Test
    void should_extend_the_coverage_to_the_end_of_the_last_upcoming_absence() {
        absenceJusquau(AUJOURDHUI.plusDays(30));
        assertThat(m.nuit.semainesDeCouverture(CENTRE, AUJOURDHUI)).isEqualTo(5);
        absenceJusquau(AUJOURDHUI.plusDays(2));
        assertThat(m.nuit.semainesDeCouverture(CENTRE, AUJOURDHUI)).isEqualTo(2);
    }

    @Test
    void should_cap_the_coverage_at_eight_weeks_and_ignore_absences_already_over() {
        absenceJusquau(AUJOURDHUI.plusDays(400));
        assertThat(m.nuit.semainesDeCouverture(CENTRE, AUJOURDHUI)).isEqualTo(8);
        absenceJusquau(AUJOURDHUI.minusDays(3));
        assertThat(m.nuit.semainesDeCouverture(CENTRE, AUJOURDHUI)).isEqualTo(2);
    }

    @Test
    void should_fall_back_to_two_weeks_when_the_upcoming_absences_cannot_be_read() {
        m.lecturePersonnelEnEchec = new IllegalStateException("illisible");
        assertThat(m.nuit.semainesDeCouverture(CENTRE, AUJOURDHUI)).isEqualTo(2);
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        assertThat(opt.demandes.getFirst().parametres().nbSemaines()).isEqualTo(2);
    }

    @Test
    void should_launch_the_coverage_over_the_whole_horizon_of_the_last_absence() {
        absenceJusquau(AUJOURDHUI.plusDays(30));
        m.nuit.replanifier(CENTRE, AUJOURDHUI);
        assertThat(opt.demandes.getFirst().parametres().nbSemaines()).isEqualTo(5);
    }

    /** Les identifiants des patients de ces tests. */
    private static final class JeuDOptimisationPatients {
        static com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId p1() {
            return com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient(1);
        }
    }
}
