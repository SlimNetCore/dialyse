package com.hemodialyse.backend.contexts.seance.interfaces.jobs;

import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.seance.application.RappelerLesSeancesARegulariser;
import com.hemodialyse.backend.contexts.seance.application.RappelerLesSeancesARegulariser.ResultatDuCentre;
import com.hemodialyse.backend.contexts.seance.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** SE4 : le planificateur du module : un déclencheur à 07:00 qui délègue au cas d'usage, sans lire l'heure. */
class RappelDesRegularisationsPlanifieTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));

    @Test
    void should_trigger_once_a_day_at_seven_in_the_planning_time_zone() throws Exception {
        Method declencheur = RappelDesRegularisationsPlanifie.class.getMethod("rappelerLesRegularisations");

        Scheduled planification = declencheur.getAnnotation(Scheduled.class);

        assertThat(planification.cron()).isEqualTo("0 0 7 * * *");
        assertThat(planification.zone()).contains("app.seance.fuseau-par-defaut").endsWith("Africa/Algiers}}}");
        long declencheurs = java.util.Arrays.stream(RappelDesRegularisationsPlanifie.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class)).count();
        assertThat(declencheurs).isEqualTo(1);
    }

    @Test
    void should_remind_every_centre_through_the_use_case_when_triggered() {
        var emissions = new ArrayList<String>();
        HistoriqueDesSeances historique = (centre, recherche, pagination) -> PagedResult.of(
                List.of(new HistoriqueDesSeances.LigneDeSeance(new SeanceId(new UUID(5, 1)), centre, new PatientId(new UUID(7, 1)), "P1", "N",
                        "P", LocalDate.of(2026, 6, 14), StatutDeSeance.CREE, null, null, null, null, null, false, null)), 1, 0, 1);
        var cas = new RappelerLesSeancesARegulariser(() -> List.of(CENTRE),
                new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-06-15T05:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC), historique,
                rappel -> emissions.add(rappel.centre().valeur() + "|" + rappel.jour()), (centre, jour) -> {
        }, new UniteDeTravail() {
            @Override
            public <T> T executer(Supplier<T> action) {
                return action.get();
            }
        });

        new RappelDesRegularisationsPlanifie(cas).rappelerLesRegularisations();

        assertThat(emissions).containsExactly(CENTRE.valeur() + "|2026-06-15");
    }

    @Test
    void should_log_incidents_and_reminders_without_ever_throwing() {
        RappelDesRegularisationsPlanifie.journaliser(List.of(
                new ResultatDuCentre(CENTRE, null, 0, false, "IllegalStateException : panne"),
                new ResultatDuCentre(CENTRE, LocalDate.of(2026, 6, 15), 2, true, null),
                new ResultatDuCentre(CENTRE, LocalDate.of(2026, 6, 15), 2, false, null),
                new ResultatDuCentre(CENTRE, LocalDate.of(2026, 6, 15), 0, false, null)));
    }
}
