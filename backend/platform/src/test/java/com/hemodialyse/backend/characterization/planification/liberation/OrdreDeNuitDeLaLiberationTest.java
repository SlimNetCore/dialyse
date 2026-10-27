package com.hemodialyse.backend.characterization.planification.liberation;

import com.hemodialyse.backend.contexts.absencespatients.interfaces.jobs.ControleDeNuitDesAbsences;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.LiberationDesPlacesDe0400;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RG-PLN-083 : ordre de nuit EXPLICITE. La détection des absences (02:30) précède la libération des places (04:00), toutes deux au
 * fuseau Africa/Algiers par défaut. Si la libération passait avant, un décédé du jour et un séjour fini la veille ne seraient plus
 * « attendus » la veille (leurs jours de dialyse seraient effacés) et le contrôle des absences les manquerait (KB-PLN-81). Ce test casse
 * si l'un des deux horaires ou fuseaux change ; le comportement observable est verrouillé par le golden {@code ordre_de_nuit}.
 */
class OrdreDeNuitDeLaLiberationTest {

    private static final Pattern PAR_DEFAUT = Pattern.compile("\\$\\{[^:}]+:(.+)}");

    private static Scheduled planifie(Class<?> type, String methode) throws Exception {
        return type.getMethod(methode).getAnnotation(Scheduled.class);
    }

    private static ZoneId fuseau(Scheduled s) {
        Matcher m = PAR_DEFAUT.matcher(s.zone());
        assertThat(m.matches()).as("fuseau configurable avec une valeur par défaut : " + s.zone()).isTrue();
        return ZoneId.of(m.group(1));
    }

    @Test
    void should_detect_the_absences_at_0230_before_freeing_the_places_at_0400_in_the_same_zone() throws Exception {
        Scheduled detection = planifie(ControleDeNuitDesAbsences.class, "controlerAbsences");
        Scheduled liberation = planifie(LiberationDesPlacesDe0400.class, "libererLesPlaces");

        assertThat(fuseau(detection)).isEqualTo(ZoneId.of("Africa/Algiers")).isEqualTo(fuseau(liberation));

        ZonedDateTime minuit = LocalDateTime.of(2026, 10, 27, 0, 0).atZone(fuseau(liberation));
        ZonedDateTime heureDeDetection = CronExpression.parse(detection.cron()).next(minuit);
        ZonedDateTime heureDeLiberation = CronExpression.parse(liberation.cron()).next(minuit);

        assertThat(heureDeDetection.toLocalTime()).hasToString("02:30");
        assertThat(heureDeLiberation.toLocalTime()).hasToString("04:00");
        assertThat(heureDeDetection).isBefore(heureDeLiberation);
        assertThat(heureDeDetection.toLocalDate()).isEqualTo(heureDeLiberation.toLocalDate());
    }
}
