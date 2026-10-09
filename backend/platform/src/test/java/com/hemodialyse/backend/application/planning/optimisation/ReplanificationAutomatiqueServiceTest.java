package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplanificationAutomatiqueServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final LocalDate MARDI = LocalDate.of(2026, 9, 29);

    private final OptimisationPlanningService planification = mock(OptimisationPlanningService.class);
    private final ReglagesOptimisationPort reglages = mock(ReglagesOptimisationPort.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PresenceDonneesPort presence = mock(PresenceDonneesPort.class);
    private final ReplanificationAutomatiqueService service =
            new ReplanificationAutomatiqueService(planification, reglages, notifications, presence);

    private static AbsenceInfirmier absence(LocalDate debut, LocalDate fin) {
        return AbsenceInfirmier.creer(CENTRE, UUID.randomUUID(), debut, fin, TypeAbsence.CONGE, null);
    }

    private void absencesAVenir(AbsenceInfirmier... absences) {
        when(presence.charger(eq(CENTRE), any(), any())).thenReturn(
                new DonneesPresence(null, 4, List.of(), List.of(), List.of(absences), List.of()));
    }

    @Test
    void should_cover_two_weeks_when_no_absence_is_planned() {
        absencesAVenir();

        assertThat(service.semainesDeCouverture(CENTRE, MARDI)).isEqualTo(2);
    }

    @Test
    void should_extend_the_coverage_up_to_the_end_of_a_future_absence() {
        // absence qui se termine le lundi 2 novembre 2026, soit la 6e semaine à partir du dimanche 27 septembre
        absencesAVenir(absence(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 2)));

        assertThat(service.semainesDeCouverture(CENTRE, MARDI)).isEqualTo(6);
    }

    @Test
    void should_cap_the_coverage_to_the_maximum_horizon_and_ignore_past_absences() {
        absencesAVenir(absence(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)),
                absence(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 3, 1)));

        assertThat(service.semainesDeCouverture(CENTRE, MARDI)).isEqualTo(ParametresOptimisation.SEMAINES_MAX);

        absencesAVenir(absence(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)));
        assertThat(service.semainesDeCouverture(CENTRE, MARDI)).isEqualTo(2);
    }

    @Test
    void should_fall_back_to_two_weeks_when_the_absences_cannot_be_read() {
        when(presence.charger(any(), any(), any())).thenThrow(new IllegalStateException("base indisponible"));

        assertThat(service.semainesDeCouverture(CENTRE, MARDI)).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_launch_the_coverage_over_the_computed_horizon() {
        absencesAVenir(absence(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 2)));
        ArgumentCaptor<ParametresOptimisation> etape = ArgumentCaptor.forClass(ParametresOptimisation.class);

        service.replanifier(CENTRE, MARDI);

        verify(planification).lancer(eq(CENTRE), etape.capture(), eq("SYSTEME"), any(Consumer.class));
        assertThat(etape.getValue().perimetre()).isEqualTo(PerimetreOptimisation.COUVERTURE);
        assertThat(etape.getValue().nbSemaines()).isEqualTo(6);
    }

    private static RunOptimisation termine(ParametresOptimisation p, ResultatOptimisation resultat) {
        return RunOptimisation.demarrer(CENTRE, p, ReplanificationAutomatiqueService.UTILISATEUR, "x", Instant.EPOCH)
                .terminer(resultat, "0hard/0medium/0soft", Instant.EPOCH.plusSeconds(5));
    }

    @Test
    void should_plan_coverage_and_maintenance_over_two_weeks_then_the_patients_of_next_week() {
        List<ParametresOptimisation> etapes = ReplanificationAutomatiqueService.etapes(MARDI);

        assertThat(etapes).extracting(ParametresOptimisation::perimetre).containsExactly(
                PerimetreOptimisation.COUVERTURE, PerimetreOptimisation.MAINTENANCE, PerimetreOptimisation.PATIENTS);
        assertThat(etapes.get(0).nbSemaines()).isEqualTo(2);
        assertThat(etapes.get(1).nbSemaines()).isEqualTo(2);
        assertThat(etapes.get(2).debutSemaine()).isEqualTo(etapes.get(0).debutSemaine().plusWeeks(1));
        // le placement des patients (semaine type) se projette lui aussi sur deux semaines parcourables
        assertThat(etapes.get(2).nbSemaines()).isEqualTo(2);
        assertThat(etapes.get(2).semainesCalcul()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_launch_each_step_when_the_previous_one_ends_and_notify_only_useful_proposals() {
        ArgumentCaptor<Consumer<RunOptimisation>> suite = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<ParametresOptimisation> etape = ArgumentCaptor.forClass(ParametresOptimisation.class);

        service.replanifier(CENTRE, MARDI);
        verify(planification).lancer(eq(CENTRE), etape.capture(), eq("SYSTEME"), suite.capture());
        assertThat(etape.getValue().perimetre()).isEqualTo(PerimetreOptimisation.COUVERTURE);

        // couverture sans rien à pourvoir : pas de notification, l'étape suivante démarre
        suite.getValue().accept(termine(etape.getValue(), OptimisationTestSupport.resultatVide()));
        verify(planification, times(2)).lancer(eq(CENTRE), etape.capture(), eq("SYSTEME"), suite.capture());
        assertThat(etape.getValue().perimetre()).isEqualTo(PerimetreOptimisation.MAINTENANCE);
        verify(notifications, never()).notifyOptimisationProposition(any(), any(), anyString(), anyString(), anyInt());

        // maintenance avec une séance à déplacer : notifiée
        Poste poste = new Poste(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "G");
        ResultatOptimisation vide = OptimisationTestSupport.resultatVide();
        ResultatOptimisation maintenance = new ResultatOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), vide.avant(), vide.apres(), List.of(new DeplacementTemporairePropose(UUID.randomUUID(), "P",
                MARDI, JourSemaine.MARDI, poste, poste, "Révision")), List.of());
        RunOptimisation run = termine(etape.getValue(), maintenance);
        suite.getValue().accept(run);
        verify(notifications).notifyOptimisationProposition(CENTRE, run.id(), "MAINTENANCE", "MAINTENANCE", 1);
        verify(planification, times(3)).lancer(eq(CENTRE), etape.capture(), eq("SYSTEME"), suite.capture());
        assertThat(etape.getValue().perimetre()).isEqualTo(PerimetreOptimisation.PATIENTS);

        // dernière étape : plus rien à lancer
        suite.getValue().accept(termine(etape.getValue(), vide));
        verify(planification, times(3)).lancer(any(), any(), anyString(), any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_skip_the_night_when_a_calculation_is_already_running() {
        when(planification.lancer(any(), any(), anyString(), any(Consumer.class)))
                .thenThrow(new BusinessException("OPTIMISATION_DEJA_EN_COURS", "en cours"));

        service.replanifier(CENTRE, MARDI);

        verify(planification, times(1)).lancer(any(), any(), anyString(), any(Consumer.class));
    }

    @Test
    void should_ignore_a_failed_run() {
        ParametresOptimisation p = ReplanificationAutomatiqueService.etapes(MARDI).getFirst();
        RunOptimisation echec = RunOptimisation.demarrer(CENTRE, p, "SYSTEME", "x", Instant.EPOCH)
                .echouer("boom", Instant.EPOCH);

        service.signaler(echec);

        verify(notifications, never()).notifyOptimisationProposition(any(), any(), anyString(), anyString(), anyInt());
    }
}
