package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

import java.util.List;

/**
 * Port sortant : les réglages de planification d'un centre.
 */
public interface ReglagesRepository {

    /** Les réglages du centre, ou les réglages par défaut tant qu'il n'en a pas enregistré. Ne crée rien. */
    ReglagesDePlanification lire(CentreId centre);

    void enregistrer(CentreId centre, ReglagesDePlanification reglages);

    /** Centres qui ont activé la replanification nocturne, par identifiant (lecture publiée pour le legacy). */
    List<CentreId> centresEnReplanificationNocturne();
}
