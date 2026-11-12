package com.hemodialyse.backend.contexts.notifications.domain;

/** Une notification ou une demande de notification qui viole une règle du contexte (centre, identifiant, type ou date absents). */
public class NotificationInvalide extends RuntimeException {

    public NotificationInvalide(String message) {
        super(message);
    }
}
