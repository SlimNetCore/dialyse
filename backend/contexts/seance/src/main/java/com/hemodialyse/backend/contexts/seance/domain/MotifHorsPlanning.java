package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Locale;

/** Motif déclaré par l'infirmier pour confirmer la séance d'un patient qui n'est pas programmé ce jour-là. */
public enum MotifHorsPlanning {
    RATTRAPAGE, URGENCE, AUTRE;

    /** Lecture tolérante à la casse et aux espaces de bord (« urgence » vaut {@code URGENCE}) ; inconnu : refus 422 (reproduit). */
    public static MotifHorsPlanning lire(String brut) {
        try {
            return valueOf(brut.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SeanceRefusee(MotifDeRefus.MOTIF_DE_DEROGATION_REQUIS, "Motif de séance hors planning inconnu : " + brut);
        }
    }
}
