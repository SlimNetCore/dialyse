package com.hemodialyse.backend.domain.seance.model;

/**
 * Motif déclaré par l'infirmier pour confirmer une séance d'un patient qui n'est pas programmé ce jour-là.
 */
public enum MotifHorsPlanning {
    RATTRAPAGE,
    URGENCE,
    AUTRE
}
