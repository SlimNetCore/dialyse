package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;

import java.util.List;
import java.util.UUID;

/**
 * Lecture des réglages de l'optimisation (un jeu par centre). Les réglages sont tenus par le contexte Planification (slice P1) :
 * ce port en est la lecture, implémentée par une ACL sur son langage publié.
 */
public interface ReglagesOptimisationPort {

    /**
     * Réglages du centre, ou les réglages par défaut tant qu'il n'en a pas enregistré.
     */
    ReglagesOptimisation lire(UUID centerId);

    /**
     * Centres qui ont activé la replanification automatique nocturne ; réservé à la tâche planifiée.
     */
    List<UUID> centresEnReplanificationAuto();
}
