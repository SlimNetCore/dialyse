package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié du Pilotage direction pour l'accès à une société (PL10b2, ADR 0152) : la plateforme (suivi STOMP des directions) demande si
 * un utilisateur est toujours habilité à suivre une société, sans connaître la garde ni les tables d'Identité et accès. Remplace
 * {@code DirectionAccessGuard.verifyMember} de la plateforme.
 */
public interface AccesALaDirection {

    /** Vrai si l'utilisateur est actif, rattaché à la société et que la société est active (revérifié à chaque appel, jamais d'après le jeton). */
    boolean estHabilite(UUID utilisateur, UUID societe);
}
