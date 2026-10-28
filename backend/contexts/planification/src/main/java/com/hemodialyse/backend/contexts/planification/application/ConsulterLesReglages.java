package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;

/**
 * Lit les réglages de planification du centre, ou les réglages par défaut ; ne crée rien.
 */
public class ConsulterLesReglages {

    private final ReglagesRepository reglages;

    public ConsulterLesReglages(ReglagesRepository reglages) {
        this.reglages = reglages;
    }

    public ReglagesDePlanification executer(CentreId centre) {
        return reglages.lire(centre);
    }
}
