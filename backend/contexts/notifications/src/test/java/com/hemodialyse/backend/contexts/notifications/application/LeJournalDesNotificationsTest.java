package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Consulter le journal, marquer lu, tout marquer lu et purger, sur le double en mémoire du journal. */
class LeJournalDesNotificationsTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-12T12:00:00Z");
    private static final CentreId A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final CentreId B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private NotificationsDuJournalEnMemoire journal;
    private ConsulterLeJournal consulter;
    private MarquerLesNotificationsLues marquer;
    private ToutMarquerLu toutMarquer;
    private PurgerLesNotifications purger;

    @BeforeEach
    void preparer() {
        journal = new NotificationsDuJournalEnMemoire();
        var session = new SessionFixe(A, "admin", Set.of("ADMIN"));
        var horloge = new HorlogeFixe(MAINTENANT);
        consulter = new ConsulterLeJournal(session, journal, horloge);
        marquer = new MarquerLesNotificationsLues(session, journal, horloge);
        toutMarquer = new ToutMarquerLu(session, journal, horloge);
        purger = new PurgerLesNotifications(journal, horloge);
    }

    private static NotificationId id(int n) {
        return new NotificationId(new UUID(0L, n));
    }

    private void emise(int n, CentreId centre, Audience audience, Duration age) {
        journal.enregistrer(Notification.emise(id(n), centre, new TypeDeNotification("T" + n), Map.of("cle", "v" + n), audience,
                MAINTENANT.minus(age)));
    }

    private static Audience pour(String... roles) {
        return Audience.desRoles(Arrays.asList(roles));
    }

    @Test
    void should_list_the_visible_notifications_of_the_centre_of_the_session_newest_first_within_thirty_days() {
        emise(1, A, pour("ADMIN"), Duration.ofMinutes(50));
        emise(2, A, pour(), Duration.ofMinutes(40));
        emise(3, A, pour("INFIRMIER"), Duration.ofMinutes(30));
        emise(4, B, pour("ADMIN"), Duration.ofMinutes(20));
        emise(5, A, pour("ADMIN"), Duration.ofDays(31));
        var consultation = consulter.executer(null, 0, 50);
        assertThat(consultation.centre()).isEqualTo(A);
        assertThat(consultation.notifications().items()).extracting(n -> n.id().valeur().getLeastSignificantBits()).containsExactly(2L, 1L);
        assertThat(consultation.notifications().total()).isEqualTo(2);
    }

    @Test
    void should_bound_the_page_size_between_one_and_one_hundred_and_start_the_page_at_zero() {
        for (int i = 1; i <= 3; i++) emise(i, A, pour(), Duration.ofMinutes(i));
        assertThat(consulter.executer(null, -3, 0).notifications().size()).isEqualTo(1);
        assertThat(consulter.executer(null, -3, 0).notifications().page()).isZero();
        assertThat(consulter.executer(null, 0, 5000).notifications().size()).isEqualTo(100);
        assertThat(consulter.executer(null, 1, 2).notifications().items()).hasSize(1);
    }

    @Test
    void should_refuse_a_centre_other_than_the_one_of_the_session() {
        assertThatThrownBy(() -> consulter.executer(B.valeur(), 0, 50)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> marquer.executer(B.valeur(), List.of())).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> toutMarquer.executer(B.valeur())).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_mark_as_read_only_for_the_user_and_stay_idempotent() {
        emise(1, A, pour("ADMIN"), Duration.ofMinutes(10));
        marquer.executer(null, List.of(id(1).valeur(), id(1).valeur(), new UUID(9, 9)));
        marquer.executer(null, List.of(id(1).valeur()));
        assertThat(journal.lecturesDe("admin")).isEqualTo(1);
        assertThat(consulter.executer(null, 0, 50).notifications().items()).extracting(NotificationDuJournal::lue).containsExactly(true);
    }

    @Test
    void should_ignore_the_marking_of_a_notification_of_another_centre() {
        emise(1, B, pour("ADMIN"), Duration.ofMinutes(10));
        marquer.executer(null, List.of(id(1).valeur()));
        assertThat(journal.lecturesDe("admin")).isZero();
    }

    @Test
    void should_ignore_the_marking_of_a_notification_whose_audience_excludes_the_user_kb_not1_3() {
        emise(1, A, pour("INFIRMIER"), Duration.ofMinutes(10));
        emise(2, A, pour("ADMIN"), Duration.ofDays(40));
        marquer.executer(null, List.of(id(1).valeur(), id(2).valeur()));
        assertThat(journal.lecturesDe("admin")).as("N02 est de l'audience, même hors fenêtre de consultation").isEqualTo(1);
    }

    @Test
    void should_reject_a_marking_without_identifiers_and_ignore_an_absent_identifier() {
        assertThatThrownBy(() -> marquer.executer(null, null)).isInstanceOf(NotificationInvalide.class);
        emise(1, A, pour("ADMIN"), Duration.ofMinutes(10));
        marquer.executer(null, Arrays.asList(null, id(1).valeur()));
        assertThat(journal.lecturesDe("admin")).isEqualTo(1);
    }

    @Test
    void should_mark_everything_visible_in_the_window_of_the_centre_as_read_and_nothing_else() {
        emise(1, A, pour("ADMIN"), Duration.ofMinutes(10));
        emise(2, A, pour("INFIRMIER"), Duration.ofMinutes(10));
        emise(3, B, pour("ADMIN"), Duration.ofMinutes(10));
        emise(4, A, pour("ADMIN"), Duration.ofDays(31));
        toutMarquer.executer(null);
        toutMarquer.executer(null);
        assertThat(journal.lecturesDe("admin")).isEqualTo(1);
    }

    @Test
    void should_purge_the_notifications_older_than_sixty_days_with_their_readings() {
        emise(1, A, pour("ADMIN"), Duration.ofDays(61));
        emise(2, A, pour("ADMIN"), Duration.ofDays(59));
        emise(3, B, pour("ADMIN"), Duration.ofDays(90));
        marquer.executer(null, List.of(id(1).valeur(), id(2).valeur()));
        assertThat(purger.executer()).isEqualTo(2);
        assertThat(journal.nombre()).isEqualTo(1);
        assertThat(journal.lecturesDe("admin")).isEqualTo(1);
        assertThat(purger.executer()).isZero();
    }
}
