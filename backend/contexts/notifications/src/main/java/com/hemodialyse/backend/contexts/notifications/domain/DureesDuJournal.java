package com.hemodialyse.backend.contexts.notifications.domain;

import java.time.Duration;

/** Le journal ne propose que les notifications des 30 derniers jours ; la purge de 03:40 conserve 60 jours (durée à confirmer, Q10). */
public final class DureesDuJournal {

    public static final Duration FENETRE_DE_CONSULTATION = Duration.ofDays(30);
    public static final Duration CONSERVATION = Duration.ofDays(60);

    private DureesDuJournal() {
    }
}
