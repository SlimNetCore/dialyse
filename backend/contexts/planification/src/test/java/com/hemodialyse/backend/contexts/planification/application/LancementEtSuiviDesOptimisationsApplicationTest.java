package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.doubles.MondeDOptimisation;
import com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation.Statut;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PaginationInvalide;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P20 / P21 / P24 avec les doubles en mémoire et un optimiseur FAUX piloté par le test : un seul calcul par centre sous le verrou du
 * centre en base, réglages du centre figés, historique de 20, progression, fin, échec, arrêt, suppression, reprise au démarrage,
 * calendrier figé.
 */
class LancementEtSuiviDesOptimisationsApplicationTest {

    private final MondeDOptimisation m = new MondeDOptimisation();
    private final OptimisationEnMemoire opt = m.optimisation;

    @BeforeEach
    void centreAvecUnPatient() {
        opt.donneesDuCentre = new JeuDOptimisation().patient(1, LMV, false, poste(A, MATIN, A1)).infirmierActif(1, false)
                .affecte(1, A, MATIN, LMV).donnees();
    }

    private ExecutionDOptimisation lancer(Perimetre perimetre) {
        return m.lancer.executer(CENTRE, ParametresDOptimisation.parDefaut(perimetre, JeuDOptimisation.LUNDI), "admin");
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (RegleDePlanificationViolee e) {
            return e.code();
        }
        return null;
    }

    @Test
    void should_return_the_execution_in_progress_and_hand_the_data_to_the_solver_after_the_transaction() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);

        assertThat(e.statut()).isEqualTo(Statut.EN_COURS);
        assertThat(e.lancePar()).isEqualTo("admin");
        assertThat(e.creeLe()).isEqualTo(MondeDOptimisation.MAINTENANT);
        assertThat(opt.executions).containsKey(e.id());
        assertThat(opt.demandes).singleElement().satisfies(d -> {
            assertThat(d.execution()).isEqualTo(e.id());
            assertThat(d.donnees()).isSameAs(opt.donneesDuCentre);
            assertThat(d.parametres()).isEqualTo(e.parametres());
        });
        assertThat(opt.demandes.getFirst().ecouteur()).isNotNull();
    }

    @Test
    void should_take_the_centre_lock_in_the_database_before_judging_that_nothing_is_running() {
        lancer(Perimetre.PATIENTS);
        assertThat(m.places.verrous).containsExactly(CENTRE);
    }

    @Test
    void should_refuse_a_second_calculation_for_the_same_centre_and_start_nothing() {
        lancer(Perimetre.PATIENTS);

        assertThat(code(() -> lancer(Perimetre.COMPLET))).isEqualTo("OPTIMISATION_DEJA_EN_COURS");
        assertThat(opt.demandes).hasSize(1);
        assertThat(opt.executions).hasSize(1);
    }

    @Test
    void should_refuse_with_a_concurrent_write_when_another_instance_holds_the_lock() {
        m.places.verrouRefuse = true;
        assertThatThrownBy(() -> lancer(Perimetre.PATIENTS)).isInstanceOf(EcritureConcurrente.class);
        assertThat(opt.executions).isEmpty();
        assertThat(opt.demandes).isEmpty();
    }

    @Test
    void should_allow_a_new_calculation_once_the_previous_one_is_finished() {
        ExecutionDOptimisation premiere = lancer(Perimetre.PATIENTS);
        opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "0hard/0soft");

        assertThat(opt.executions.get(premiere.id()).statut()).isEqualTo(Statut.TERMINEE);
        assertThat(lancer(Perimetre.PATIENTS).id()).isNotEqualTo(premiere.id());
    }

    @Test
    void should_allow_a_calculation_for_another_centre() {
        lancer(Perimetre.PATIENTS);
        ExecutionDOptimisation autre = m.lancer.executer(new com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId(
                UUID.randomUUID()), ParametresDOptimisation.parDefaut(Perimetre.PATIENTS, JeuDOptimisation.LUNDI), "admin");
        assertThat(autre.enCours()).isTrue();
    }

    @Test
    void should_freeze_the_staff_settings_of_the_centre_in_the_parameters() {
        m.parametrage.reglages.enregistrer(CENTRE, new ReglagesDePlanification(false, 4, 35, 2));

        ParametresDOptimisation p = lancer(Perimetre.ROULEMENT).parametres();

        assertThat(p.heuresParVacation()).isEqualTo(4);
        assertThat(p.heuresHebdoTempsPlein()).isEqualTo(35);
        assertThat(p.reposHebdoMin()).isEqualTo(2);
        assertThat(opt.derniereDemande().parametres()).isEqualTo(p);
    }

    @Test
    void should_use_the_default_staff_settings_when_the_centre_has_none() {
        ParametresDOptimisation p = lancer(Perimetre.ROULEMENT).parametres();
        assertThat(p.heuresParVacation()).isEqualTo(5);
        assertThat(p.heuresHebdoTempsPlein()).isEqualTo(40);
        assertThat(p.reposHebdoMin()).isEqualTo(1);
    }

    @Test
    void should_keep_the_fingerprint_of_the_data_read_at_the_launch() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        assertThat(e.empreinte()).isEqualTo(
                com.hemodialyse.backend.contexts.planification.domain.Empreinte.calculer(opt.donneesDuCentre, e.parametres()));
    }

    @Test
    void should_keep_only_the_twenty_latest_executions_of_the_centre_and_purge_orphan_calendars() {
        List<ExecutionDOptimisation> lancees = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            m.maintenant = MondeDOptimisation.MAINTENANT.plusSeconds(i);
            lancees.add(lancer(Perimetre.PATIENTS));
            opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "s");
        }
        assertThat(opt.executions).hasSize(20);
        assertThat(opt.executions).doesNotContainKey(lancees.getFirst().id());
        assertThat(opt.calendriersEnregistres).doesNotContainKey(lancees.getFirst().id());
    }

    @Test
    void should_record_the_progress_at_most_once_per_second() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        OptimisationEnMemoire.Demande demande = opt.derniereDemande();

        demande.ecouteur().progression("PATIENTS", "0hard/-5soft");
        m.maintenant = m.maintenant.plus(Duration.ofMillis(300));
        demande.ecouteur().progression("PATIENTS", "0hard/-4soft");
        assertThat(opt.executions.get(e.id()).score()).isEqualTo("0hard/-5soft");

        m.maintenant = m.maintenant.plus(Duration.ofSeconds(1));
        demande.ecouteur().progression("PATIENTS", "0hard/-3soft");
        assertThat(opt.executions.get(e.id()).score()).isEqualTo("0hard/-3soft");
        assertThat(opt.executions.get(e.id()).enCours()).isTrue();
    }

    @Test
    void should_finish_with_the_result_the_score_and_a_frozen_calendar() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        m.maintenant = m.maintenant.plusSeconds(30);

        opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "0hard/0soft");

        ExecutionDOptimisation terminee = opt.executions.get(e.id());
        assertThat(terminee.statut()).isEqualTo(Statut.TERMINEE);
        assertThat(terminee.termineLe()).isEqualTo(m.maintenant);
        assertThat(terminee.resume()).isNotNull();
        assertThat(opt.calendriersEnregistres).containsKey(e.id());
        assertThat(m.consulter.semaines(CENTRE, e.id())).containsExactly(JeuDOptimisation.DIMANCHE);
        assertThat(m.consulter.calendrier(CENTRE, e.id(), LocalDate.of(2026, 11, 4))).hasSize(1);
    }

    @Test
    void should_fail_with_the_message_of_the_solver() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        opt.derniereDemande().ecouteur().echec("pas de solution");
        assertThat(opt.executions.get(e.id()).statut()).isEqualTo(Statut.ECHEC);
        assertThat(opt.executions.get(e.id()).erreur()).isEqualTo("pas de solution");
    }

    @Test
    void should_mark_the_execution_failed_and_rethrow_when_the_solver_cannot_start() {
        opt.demarrageImpossible = true;
        assertThatThrownBy(() -> lancer(Perimetre.PATIENTS)).isInstanceOf(IllegalStateException.class);
        assertThat(opt.executions.values()).singleElement().satisfies(e -> assertThat(e.statut()).isEqualTo(Statut.ECHEC));
        // le centre n'est pas bloqué
        opt.demarrageImpossible = false;
        assertThat(lancer(Perimetre.PATIENTS).enCours()).isTrue();
    }

    @Test
    void should_call_the_continuation_at_the_end_with_or_without_success_and_survive_its_failure() {
        List<ExecutionDOptimisation> fins = new ArrayList<>();
        m.lancer.executer(CENTRE, ParametresDOptimisation.parDefaut(Perimetre.PATIENTS, JeuDOptimisation.LUNDI), "SYSTEME", fins::add);
        opt.derniereDemande().ecouteur().echec("x");
        assertThat(fins).singleElement().satisfies(f -> assertThat(f.statut()).isEqualTo(Statut.ECHEC));

        m.lancer.executer(CENTRE, ParametresDOptimisation.parDefaut(Perimetre.PATIENTS, JeuDOptimisation.LUNDI), "SYSTEME", f -> {
            throw new IllegalStateException("suite en échec");
        });
        opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "s");
        assertThat(opt.executions.values()).allMatch(e -> !e.enCours());
    }

    @Test
    void should_not_fail_the_solver_when_the_calendar_cannot_be_built() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        // un déplacement temporaire sans salle fait échouer la construction du calendrier : l'exécution reste terminée et applicable
        ResultatDOptimisation bancal = new ResultatDOptimisation(null, null, null, null, null, null, null, null,
                List.of(new ResultatDOptimisation.DeplacementTemporairePropose(JeuDePlaces.patient(1), "P", JeuDOptimisation.LUNDI,
                        com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.LUNDI, null,
                        new com.hemodialyse.backend.contexts.planification.domain.Poste(null, null, null, "x"), null)), null);
        opt.derniereDemande().ecouteur().termine(bancal, "s");
        assertThat(opt.executions.get(e.id()).statut()).isEqualTo(Statut.TERMINEE);
        assertThat(opt.calendriersEnregistres).doesNotContainKey(e.id());
    }

    @Test
    void should_ask_the_solver_to_stop_a_calculation_in_progress_and_return_the_state_before_the_stop() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);

        ExecutionDOptimisation rendue = m.arreter.executer(CENTRE, e.id());

        assertThat(rendue.enCours()).isTrue();
        assertThat(opt.arrets).containsExactly(e.id());
    }

    @Test
    void should_do_nothing_when_stopping_a_finished_calculation() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "s");

        assertThat(m.arreter.executer(CENTRE, e.id()).statut()).isEqualTo(Statut.TERMINEE);
        assertThat(opt.arrets).isEmpty();
    }

    @Test
    void should_report_an_unknown_or_foreign_execution_as_not_found_everywhere() {
        ExecutionId inconnue = new ExecutionId(UUID.randomUUID());
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        var autreCentre = new com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId(UUID.randomUUID());

        assertThat(code(() -> m.arreter.executer(CENTRE, inconnue))).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(code(() -> m.arreter.executer(autreCentre, e.id()))).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(code(() -> m.consulter.execution(autreCentre, e.id()))).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(code(() -> m.consulter.semaines(CENTRE, inconnue))).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(code(() -> m.consulter.calendrier(CENTRE, inconnue, JeuDOptimisation.LUNDI))).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(code(() -> m.supprimer.executer(autreCentre, e.id()))).isEqualTo("OPTIMISATION_INTROUVABLE");
    }

    @Test
    void should_refuse_to_delete_a_calculation_in_progress_then_delete_it_with_its_calendar_once_finished() {
        ExecutionDOptimisation e = lancer(Perimetre.PATIENTS);
        assertThat(code(() -> m.supprimer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_SUPPRESSION_EN_COURS");
        assertThat(opt.executions).containsKey(e.id());

        opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "s");
        m.supprimer.executer(CENTRE, e.id());

        assertThat(opt.executions).doesNotContainKey(e.id());
        assertThat(opt.calendriersEnregistres).doesNotContainKey(e.id());
    }

    @Test
    void should_page_the_history_from_the_most_recent_and_refuse_negative_pagination() {
        for (int i = 0; i < 3; i++) {
            m.maintenant = MondeDOptimisation.MAINTENANT.plusSeconds(i);
            lancer(Perimetre.PATIENTS);
            opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "s");
        }
        var page = m.consulter.historique(CENTRE, new Pagination(0, 2));
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.elements()).hasSize(2);
        assertThat(page.elements().getFirst().creeLe()).isAfter(page.elements().getLast().creeLe());
        assertThatThrownBy(() -> new Pagination(-1, 10)).isInstanceOf(PaginationInvalide.class);
        assertThatThrownBy(() -> new Pagination(0, -1)).isInstanceOf(PaginationInvalide.class);
        assertThat(new Pagination(0, 1000).taille()).isEqualTo(100);
    }

    @Test
    void should_fail_the_calculations_left_in_progress_by_a_restart() {
        ExecutionDOptimisation enCours = lancer(Perimetre.PATIENTS);
        opt.derniereDemande().ecouteur().termine(OptimisationEnMemoire.resultatVide(), "s");
        ExecutionDOptimisation orpheline = lancer(Perimetre.COMPLET);

        int n = m.reprendre.executer();

        assertThat(n).isEqualTo(1);
        assertThat(opt.executions.get(orpheline.id()).statut()).isEqualTo(Statut.ECHEC);
        assertThat(opt.executions.get(orpheline.id()).erreur()).isEqualTo(ReprendreLesExecutionsInterrompues.MOTIF);
        assertThat(opt.executions.get(enCours.id()).statut()).isEqualTo(Statut.TERMINEE);
        assertThat(lancer(Perimetre.PATIENTS).enCours()).isTrue();
    }

    @Test
    void should_default_to_nothing_when_no_calculation_ever_ran() {
        assertThat(m.consulter.historique(CENTRE, new Pagination(0, 20)).elements()).isEmpty();
        assertThat(JeuDePlaces.CENTRE).isEqualTo(CENTRE);
    }
}
