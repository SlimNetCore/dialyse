package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;

import java.time.Instant;

public class HorlogeFixe implements Horloge {
    private Instant maintenant;

    public HorlogeFixe(Instant maintenant) {
        this.maintenant = maintenant;
    }

    public void avancerA(Instant instant) {
        this.maintenant = instant;
    }

    @Override
    public Instant maintenant() {
        return maintenant;
    }
}
