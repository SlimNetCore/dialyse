package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.UUID;

/** Port sortant : l'utilisateur est-il actif, rattaché à la société, et la société active ? (revérifié en base à chaque appel). */
public interface HabilitationDeLaDirection {

    boolean estHabilite(UUID utilisateur, UUID societe);
}
