package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;

import java.time.Instant;
import java.util.Map;

/** Une notification telle que la voit l'utilisateur qui consulte le journal ({@code lue} : lue par lui). */
public record NotificationDuJournal(NotificationId id, TypeDeNotification type, Map<String, String> contenu, Instant creeLe, boolean lue) {
}
