package com.hemodialyse.backend.contexts.gmao.infrastructure.systeme;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;

import java.time.Instant;

/** Adapter du port {@link Horloge} : horloge système (UTC). */
public class HorlogeSysteme implements Horloge {
    @Override
    public Instant maintenant() {
        return Instant.now();
    }
}
