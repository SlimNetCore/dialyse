package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.application.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'alerte « libération des places en échec » : une seule par centre et par jour du centre (clé déterministe), à l'administrateur.
 */
class NotificationsDeLaLiberationLegacyTest {

    static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final LocalDate JOUR = LocalDate.of(2026, 10, 27);

    JournalDesNotifications journal = Mockito.mock(JournalDesNotifications.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    NotificationsDeLaLiberationLegacy acl = new NotificationsDeLaLiberationLegacy(journal, notifications);

    @Test
    void should_derive_the_same_key_for_the_same_centre_and_day_and_another_one_otherwise() {
        assertThat(NotificationsDeLaLiberationLegacy.identifiant(CENTRE, JOUR)).isEqualTo(NotificationsDeLaLiberationLegacy.identifiant(CENTRE, JOUR));
        assertThat(NotificationsDeLaLiberationLegacy.identifiant(CENTRE, JOUR))
                .isNotEqualTo(NotificationsDeLaLiberationLegacy.identifiant(CENTRE, JOUR.plusDays(1)))
                .isNotEqualTo(NotificationsDeLaLiberationLegacy.identifiant(UUID.randomUUID(), JOUR));
    }

    @Test
    void should_emit_the_alert_once() {
        UUID id = NotificationsDeLaLiberationLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false);
        assertThat(acl.liberationEnEchec(CENTRE, JOUR, 2)).isTrue();
        verify(notifications).notifyLiberationDesPlacesEnEchec(CENTRE, JOUR, 2, id);
    }

    @Test
    void should_not_send_again_an_alert_already_in_the_journal() {
        when(journal.existe(any())).thenReturn(true);
        assertThat(acl.liberationEnEchec(CENTRE, JOUR, 2)).isFalse();
        verify(notifications, never()).notifyLiberationDesPlacesEnEchec(any(), any(), Mockito.anyInt(), any());
    }

    @Test
    void should_consider_the_alert_emitted_when_another_instance_inserted_the_same_key_first() {
        UUID id = NotificationsDeLaLiberationLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false, true);
        Mockito.doThrow(new DataIntegrityViolationException("doublon")).when(notifications)
                .notifyLiberationDesPlacesEnEchec(eq(CENTRE), eq(JOUR), eq(1), eq(id));
        assertThat(acl.liberationEnEchec(CENTRE, JOUR, 1)).isFalse();
    }

    @Test
    void should_rethrow_a_real_integrity_failure() {
        when(journal.existe(any())).thenReturn(false);
        Mockito.doThrow(new DataIntegrityViolationException("autre")).when(notifications)
                .notifyLiberationDesPlacesEnEchec(any(), any(), Mockito.anyInt(), any());
        assertThatThrownBy(() -> acl.liberationEnEchec(CENTRE, JOUR, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
