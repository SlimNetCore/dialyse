package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * L'heure du Pilotage. « Aujourd'hui » est la date du serveur dans le fuseau de la JVM (KB-DIR-03, statu quo reproduit : le legacy ne lit
 * jamais {@code centers.fuseau_horaire}) ; l'horodatage de constat est en UTC. La {@link Clock} est injectée pour que la décision
 * ultérieure (fuseau de la société, d'un centre de référence ou {@code Africa/Algiers}, question ouverte de l'ADR 0102) ne touche
 * qu'un endroit.
 */
public final class HorlogeDuPilotage {

    private final Clock horloge;

    public HorlogeDuPilotage(Clock horloge) {
        this.horloge = horloge;
    }

    public LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    public OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge.withZone(ZoneOffset.UTC));
    }
}
