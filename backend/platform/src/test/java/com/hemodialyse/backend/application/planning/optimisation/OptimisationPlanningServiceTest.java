package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.RunsEnMemoire;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation.StatutRun;
import com.hemodialyse.backend.domain.planning.optimisation.port.CalendrierPropositionPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort.Ecouteur;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.T0;
import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.parametres;
import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.resultatVide;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimisationPlanningServiceTest {

    private static final UUID C1 = UUID.randomUUID();
    private static final UUID C2 = UUID.randomUUID();

    private final OptimisationDonneesPort donnees = mock(OptimisationDonneesPort.class);
    private final OptimiseurPlanningPort optimiseur = mock(OptimiseurPlanningPort.class);
    private final com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort reglages =
            mock(com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort.class);
    private final CalendrierPropositionPort calendriers = mock(CalendrierPropositionPort.class);
    private final RunsEnMemoire runs = new RunsEnMemoire();
    private Instant maintenant = T0;
    private OptimisationPlanningService service;

    @BeforeEach
    void setUp() {
        when(donnees.charger(any(), any(), any())).thenReturn(OptimisationTestSupport.donneesVides());
        when(reglages.lire(any())).thenReturn(com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation.parDefaut());
        service = new OptimisationPlanningService(donnees, runs, optimiseur, reglages, calendriers, new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return maintenant;
            }
        });
    }

    private Ecouteur ecouteurDuDernierLancement(UUID runId) {
        ArgumentCaptor<Ecouteur> ecouteur = ArgumentCaptor.forClass(Ecouteur.class);
        verify(optimiseur).demarrer(eq(runId), any(), any(), ecouteur.capture());
        return ecouteur.getValue();
    }

    @Test
    void should_start_a_run_scoped_to_the_center_and_hand_it_to_the_optimiser() {
        ParametresOptimisation parametres = parametres(PerimetreOptimisation.PATIENTS);

        RunOptimisation run = service.lancer(C1, parametres, "admin-1");

        assertThat(run.statut()).isEqualTo(StatutRun.EN_COURS);
        assertThat(run.centerId()).isEqualTo(C1);
        assertThat(run.lancePar()).isEqualTo("admin-1");
        assertThat(run.empreinte()).hasSize(64);
        assertThat(runs.parId).containsKey(run.id());
        verify(donnees).charger(C1, parametres.debutSemaine(), parametres.finHorizon());
        verify(optimiseur).demarrer(eq(run.id()), any(), eq(parametres), any());
        assertThat(runs.journal).contains("PURGE:" + OptimisationPlanningService.HISTORIQUE_MAX);
    }

    @Test
    void should_refuse_a_second_run_while_one_is_in_progress_for_the_same_center_only() {
        service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        assertThatThrownBy(() -> service.lancer(C1, parametres(PerimetreOptimisation.ROULEMENT), "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_DEJA_EN_COURS");
        assertThat(service.lancer(C2, parametres(PerimetreOptimisation.PATIENTS), "admin").centerId()).isEqualTo(C2);
    }

    @Test
    void should_store_the_result_and_free_the_center_when_the_optimiser_finishes() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");
        Ecouteur ecouteur = ecouteurDuDernierLancement(run.id());

        maintenant = T0.plusSeconds(20);
        ecouteur.termine(resultatVide(), "0hard/0medium/-10soft");

        RunOptimisation fini = service.consulter(C1, run.id());
        assertThat(fini.statut()).isEqualTo(StatutRun.TERMINEE);
        assertThat(fini.score()).isEqualTo("0hard/0medium/-10soft");
        assertThat(fini.termineLe()).isEqualTo(T0.plusSeconds(20));
        assertThat(fini.resume()).isNotNull();
        assertThat(service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin").enCours()).isTrue();
    }

    @Test
    void should_freeze_the_calendar_of_the_proposal_for_the_center_when_the_optimiser_finishes() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        ecouteurDuDernierLancement(run.id()).termine(resultatVide(), "0hard/0medium/0soft");

        verify(calendriers).enregistrer(eq(C1), eq(run.id()), any());
    }

    @Test
    void should_not_freeze_a_calendar_when_the_run_fails() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        ecouteurDuDernierLancement(run.id()).echec("boom");

        verify(calendriers, never()).enregistrer(any(), any(), any());
    }

    @Test
    void should_keep_the_proposal_when_its_calendar_cannot_be_built() {
        org.mockito.Mockito.doThrow(new IllegalStateException("base inaccessible")).when(calendriers)
                .enregistrer(any(), any(), any());
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        ecouteurDuDernierLancement(run.id()).termine(resultatVide(), "0hard/0medium/0soft");

        assertThat(service.consulter(C1, run.id()).statut()).isEqualTo(StatutRun.TERMINEE);
    }

    @Test
    void should_purge_the_calendars_of_runs_that_left_the_history_of_the_center() {
        service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        verify(calendriers).purgerOrphelins(C1);
    }

    @Test
    void should_only_read_the_calendar_of_a_run_of_the_center() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        assertThatThrownBy(() -> service.calendrier(C2, run.id(), OptimisationTestSupport.DIMANCHE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThatThrownBy(() -> service.semainesCalendrier(C2, run.id())).isInstanceOf(BusinessException.class);
        verify(calendriers, never()).lire(any(), any(), any());

        service.calendrier(C1, run.id(), OptimisationTestSupport.DIMANCHE.plusDays(3));
        verify(calendriers).lire(C1, run.id(), OptimisationTestSupport.DIMANCHE);
    }

    @Test
    void should_delete_a_finished_run_of_the_center_with_its_calendar() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");
        ecouteurDuDernierLancement(run.id()).termine(resultatVide(), "0hard/0medium/0soft");

        service.supprimer(C1, run.id());

        assertThatThrownBy(() -> service.consulter(C1, run.id())).isInstanceOf(BusinessException.class);
        verify(calendriers, org.mockito.Mockito.atLeast(2)).purgerOrphelins(C1);
    }

    @Test
    void should_refuse_to_delete_a_running_calculation() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        assertThatThrownBy(() -> service.supprimer(C1, run.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_SUPPRESSION_EN_COURS");
        assertThat(service.consulter(C1, run.id())).isNotNull();
    }

    @Test
    void should_not_delete_a_run_of_another_center() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");
        ecouteurDuDernierLancement(run.id()).termine(resultatVide(), "0hard/0medium/0soft");

        assertThatThrownBy(() -> service.supprimer(C2, run.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(service.consulter(C1, run.id())).isNotNull();
    }

    @Test
    void should_store_the_failure_message() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        ecouteurDuDernierLancement(run.id()).echec("boom");

        assertThat(service.consulter(C1, run.id())).satisfies(r -> {
            assertThat(r.statut()).isEqualTo(StatutRun.ECHEC);
            assertThat(r.erreur()).isEqualTo("boom");
        });
    }

    @Test
    void should_record_progress_at_most_once_per_second() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");
        Ecouteur ecouteur = ecouteurDuDernierLancement(run.id());
        runs.journal.clear();

        ecouteur.progression("PATIENTS", "s1");
        maintenant = T0.plusMillis(300);
        ecouteur.progression("PATIENTS", "s2");
        maintenant = T0.plusMillis(1500);
        ecouteur.progression("INFIRMIERS", "s3");

        assertThat(runs.journal).containsExactly("EN_COURS:PATIENTS", "EN_COURS:INFIRMIERS");
        assertThat(service.consulter(C1, run.id()).score()).isEqualTo("s3");
    }

    @Test
    void should_keep_the_engine_running_when_the_history_cannot_be_written() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");
        Ecouteur ecouteur = ecouteurDuDernierLancement(run.id());
        runs.saveEnEchec = true;

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            ecouteur.progression("PATIENTS", "s1");
            ecouteur.termine(resultatVide(), "s");
            ecouteur.echec("boom");
        });
    }

    @Test
    void should_not_reveal_a_run_of_another_center() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        assertThatThrownBy(() -> service.consulter(C2, run.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_INTROUVABLE");
        assertThat(service.historique(C2, 0, 20).items()).isEmpty();
        assertThat(service.historique(C1, 0, 20).items()).hasSize(1);
    }

    @Test
    void should_stop_only_a_run_in_progress_of_the_center() {
        RunOptimisation run = service.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin");

        assertThatThrownBy(() -> service.arreter(C2, run.id())).isInstanceOf(BusinessException.class);
        verify(optimiseur, never()).arreter(any());

        service.arreter(C1, run.id());
        verify(optimiseur).arreter(run.id());

        ecouteurDuDernierLancement(run.id()).termine(resultatVide(), "s");
        service.arreter(C1, run.id());
        verify(optimiseur, org.mockito.Mockito.times(1)).arreter(run.id());
    }
}
