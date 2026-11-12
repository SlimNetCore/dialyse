package com.hemodialyse.backend.contexts.notifications.interfaces.planifie;

import com.hemodialyse.backend.contexts.notifications.application.PurgerLesNotifications;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PurgeNocturneDesNotificationsTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-12T03:40:00Z");

    @Test
    void should_run_every_day_at_0340() throws Exception {
        Scheduled planification = PurgeNocturneDesNotifications.class.getMethod("purger").getAnnotation(Scheduled.class);
        assertThat(planification.cron()).isEqualTo("0 40 3 * * *");
    }

    @Test
    void should_purge_the_notifications_older_than_sixty_days() {
        var journal = new NotificationsDuJournalEnMemoire();
        CentreId centre = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        journal.enregistrer(Notification.emise(new NotificationId(new UUID(0, 1)), centre, new TypeDeNotification("T"), Map.of(),
                Audience.desRoles(List.of()), MAINTENANT.minus(Duration.ofDays(61))));
        journal.enregistrer(Notification.emise(new NotificationId(new UUID(0, 2)), centre, new TypeDeNotification("T"), Map.of(),
                Audience.desRoles(List.of()), MAINTENANT.minus(Duration.ofDays(59))));
        new PurgeNocturneDesNotifications(new PurgerLesNotifications(journal, new HorlogeFixe(MAINTENANT))).purger();
        assertThat(journal.nombre()).isEqualTo(1);
    }

    @Test
    void should_swallow_a_failure_of_the_purge_so_that_the_next_night_runs() {
        var journal = new NotificationsDuJournalEnMemoire() {
            @Override
            public int purger(Instant avant) {
                throw new IllegalStateException("base indisponible");
            }
        };
        var purge = new PurgeNocturneDesNotifications(new PurgerLesNotifications(journal, new HorlogeFixe(MAINTENANT)));
        assertThatCode(purge::purger).doesNotThrowAnyException();
    }
}
