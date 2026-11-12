package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notifier en deux temps : journaliser tout de suite (dans la transaction de l'émetteur), diffuser plus tard (après le commit). */
class JournaliserLaNotificationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID DETERMINISTE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private NotificationsDuJournalEnMemoire journal;
    private JournaliserLaNotification cas;

    @BeforeEach
    void preparer() {
        journal = new NotificationsDuJournalEnMemoire();
        cas = new JournaliserLaNotification(journal, new HorlogeFixe(MAINTENANT), new IdentifiantsSequentiels());
    }

    private static Emission emission(UUID deterministe, boolean journalisee) {
        return new Emission(CENTRE, "GENERATEUR_INDISPONIBLE", Map.of("generateur", "G1"), Audience.desRoles(List.of("ADMIN")), deterministe,
                journalisee);
    }

    @Test
    void should_record_at_once_and_hand_back_what_is_to_broadcast_after_the_commit() {
        var aEnvoyer = cas.executer(emission(null, true)).orElseThrow();
        assertThat(journal.nombre()).isEqualTo(1);
        assertThat(aEnvoyer.identifiant()).isEqualTo(new UUID(0L, 1L));
        assertThat(aEnvoyer.centre()).isEqualTo(CENTRE);
        assertThat(aEnvoyer.type()).isEqualTo("GENERATEUR_INDISPONIBLE");
        assertThat(aEnvoyer.emisLe()).isEqualTo(MAINTENANT);
        assertThat(aEnvoyer.contenu()).containsEntry("generateur", "G1").containsEntry("targetRoles", "ADMIN");
        assertThat(aEnvoyer.panneDuJournal()).isNull();
    }

    @Test
    void should_keep_the_order_of_the_keys_of_the_content_and_the_position_of_the_audience_keys() {
        var contenu = new LinkedHashMap<String, String>();
        contenu.put("b", "1");
        contenu.put("targetRoles", "ADMIN");
        contenu.put("a", "2");
        var aEnvoyer = cas.executer(new Emission(CENTRE, "T", contenu, Audience.desRoles(List.of("ADMIN")), null, false)).orElseThrow();
        assertThat(aEnvoyer.contenu().keySet()).containsExactly("b", "targetRoles", "a");
    }

    @Test
    void should_add_no_audience_key_when_the_notification_is_open_to_everybody() {
        var aEnvoyer = cas.executer(new Emission(CENTRE, "T", Map.of("a", "1"), Audience.desRoles(List.of()), null, false)).orElseThrow();
        assertThat(aEnvoyer.contenu()).containsOnlyKeys("a");
    }

    @Test
    void should_not_record_a_notification_that_is_only_broadcast() {
        var aEnvoyer = cas.executer(emission(null, false)).orElseThrow();
        assertThat(journal.nombre()).isZero();
        assertThat(aEnvoyer.identifiant()).isNull();
    }

    @Test
    void should_hand_back_nothing_for_a_replayed_fact_already_in_the_journal() {
        assertThat(cas.executer(emission(DETERMINISTE, true))).isPresent();
        assertThat(cas.executer(emission(DETERMINISTE, true))).isEmpty();
        assertThat(journal.nombre()).isEqualTo(1);
        assertThat(journal.existe(new NotificationId(DETERMINISTE))).isTrue();
    }

    @Test
    void should_swallow_a_journal_failure_without_imposed_identifier_and_still_hand_back_the_broadcast_without_identifier() {
        var panne = new IllegalStateException("base indisponible");
        journal.panne = panne;
        var aEnvoyer = cas.executer(emission(null, true)).orElseThrow();
        assertThat(aEnvoyer.identifiant()).isNull();
        assertThat(aEnvoyer.panneDuJournal()).isSameAs(panne);
    }

    @Test
    void should_propagate_a_journal_failure_when_the_identifier_is_imposed() {
        journal.panne = new IllegalStateException("base indisponible");
        assertThatThrownBy(() -> cas.executer(emission(DETERMINISTE, true))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_reject_a_request_without_centre() {
        assertThatThrownBy(() -> cas.executer(new Emission(null, "T", Map.of(), Audience.desRoles(List.of()), null, true)))
                .isInstanceOf(NotificationInvalide.class);
    }
}
