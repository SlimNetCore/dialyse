package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;

import java.util.List;
import java.util.UUID;

/**
 * Port de persistance des réglages de l'optimisation (un jeu par centre — AGENTS.md §2).
 */
public interface ReglagesOptimisationPort {

    /**
     * Réglages du centre, ou les réglages par défaut tant qu'il n'en a pas enregistré.
     */
    ReglagesOptimisation lire(UUID centerId);

    void enregistrer(UUID centerId, ReglagesOptimisation reglages);

    /**
     * Centres qui ont activé la replanification automatique nocturne ; réservé à la tâche planifiée.
     */
    List<UUID> centresEnReplanificationAuto();
}
