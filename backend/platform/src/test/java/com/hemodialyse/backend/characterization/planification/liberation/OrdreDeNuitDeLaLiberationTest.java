package com.hemodialyse.backend.characterization.planification.liberation;

import com.hemodialyse.backend.contexts.absencespatients.interfaces.jobs.ControleDeNuitDesAbsences;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.LiberationDesPlacesDe0400;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.ReplanificationDe0230;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RG-PLN-111 : ordre de nuit EXPLICITE et UNE seule source de configuration. La détection des absences (02:30), la replanification
 * automatique (02:30) et la libération des places (04:00) lisent la même clé {@code app.planification.fuseau-par-defaut}
 * (Africa/Algiers par défaut) : changer cette clé déplace les trois ensemble, la libération reste donc toujours APRÈS la détection.
 * Si elle passait avant, un décédé du jour et un séjour fini la veille ne seraient plus « attendus » la veille (leurs jours de dialyse
 * seraient effacés) et le contrôle des absences les manquerait (KB-PLN-81). Le comportement observable est aussi verrouillé par le
 * golden {@code ordre_de_nuit}.
 */
class OrdreDeNuitDeLaLiberationTest {

    private static Scheduled planifie(Class<?> type, String methode) throws Exception {
        return type.getMethod(methode).getAnnotation(Scheduled.class);
    }

    /** Le fuseau tel que Spring le résout dans l'environnement donné. */
    private static ZoneId fuseau(Scheduled s, MockEnvironment environnement) {
        return ZoneId.of(environnement.resolveRequiredPlaceholders(s.zone()));
    }

    @Test
    void should_detect_the_absences_at_0230_before_freeing_the_places_at_0400_in_the_same_zone() throws Exception {
        Scheduled detection = planifie(ControleDeNuitDesAbsences.class, "controlerAbsences");
        Scheduled replanification = planifie(ReplanificationDe0230.class, "replanifier");
        Scheduled liberation = planifie(LiberationDesPlacesDe0400.class, "libererLesPlaces");
        MockEnvironment parDefaut = new MockEnvironment();

        assertThat(fuseau(detection, parDefaut)).isEqualTo(ZoneId.of("Africa/Algiers"));
        assertThat(fuseau(replanification, parDefaut)).isEqualTo(ZoneId.of("Africa/Algiers"));
        assertThat(fuseau(liberation, parDefaut)).isEqualTo(ZoneId.of("Africa/Algiers"));

        ZonedDateTime minuit = LocalDateTime.of(2026, 10, 27, 0, 0).atZone(fuseau(liberation, parDefaut));
        ZonedDateTime heureDeDetection = CronExpression.parse(detection.cron()).next(minuit);
        ZonedDateTime heureDeLiberation = CronExpression.parse(liberation.cron()).next(minuit);

        assertThat(heureDeDetection.toLocalTime()).hasToString("02:30");
        assertThat(CronExpression.parse(replanification.cron()).next(minuit).toLocalTime()).hasToString("02:30");
        assertThat(heureDeLiberation.toLocalTime()).hasToString("04:00");
        assertThat(heureDeDetection).isBefore(heureDeLiberation);
        assertThat(heureDeDetection.toLocalDate()).isEqualTo(heureDeLiberation.toLocalDate());
    }

    @Test
    void should_move_the_three_night_jobs_together_when_the_single_zone_key_is_set() throws Exception {
        MockEnvironment environnement = new MockEnvironment().withProperty("app.planification.fuseau-par-defaut", "Pacific/Auckland");

        for (Scheduled job : new Scheduled[]{
                planifie(ControleDeNuitDesAbsences.class, "controlerAbsences"),
                planifie(ReplanificationDe0230.class, "replanifier"),
                planifie(LiberationDesPlacesDe0400.class, "libererLesPlaces")}) {
            assertThat(fuseau(job, environnement)).as(job.cron()).isEqualTo(ZoneId.of("Pacific/Auckland"));
        }
    }
}
