package com.hemodialyse.backend.contexts.notifications.infrastructure.systeme;

import com.hemodialyse.backend.contexts.notifications.application.Horloge;

import java.time.Instant;

/** L'horloge du système : les dates du journal sont des instants UTC. */
public class HorlogeDuSysteme implements Horloge {

    @Override
    public Instant maintenant() {
        return Instant.now();
    }
}
