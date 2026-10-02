package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.PlanningParametres;

import java.util.UUID;

/**
 * Port de persistance du paramétrage du planning (un jeu de paramètres par centre — AGENTS.md §2).
 */
public interface PlanningParametresPort {

    /**
     * Paramètres du centre, ou les paramètres par défaut tant qu'il n'en a pas enregistré.
     */
    PlanningParametres lire(UUID centerId);

    void enregistrer(UUID centerId, PlanningParametres parametres);
}
