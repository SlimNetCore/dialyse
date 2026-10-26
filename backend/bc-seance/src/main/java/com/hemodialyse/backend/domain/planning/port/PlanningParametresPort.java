package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.PlanningParametres;

import java.util.UUID;

/**
 * Lecture du paramétrage du planning (un jeu de paramètres par centre). Le paramétrage est tenu par le contexte Planification
 * (slice P1) : ce port en est la lecture, implémentée par une ACL sur son langage publié ; l'écriture n'existe plus ici.
 */
public interface PlanningParametresPort {

    /**
     * Paramètres du centre, ou les paramètres par défaut tant qu'il n'en a pas enregistré.
     */
    PlanningParametres lire(UUID centerId);
}
