package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Confirmation explicite d'une séance hors planning : motif, précision (obligatoire pour « autre », rognée, 255 caractères au plus) et
 * indication que l'auteur est administrateur (seul habilité à franchir un jour de fermeture du centre). Objet-valeur immuable.
 */
public record DerogationHorsPlanning(MotifHorsPlanning motif, String precision, boolean administrateur) {

    /** Colonne {@code seances.precision_hors_planning} : 255 caractères (KB-SEA-04, 400 au-delà). */
    public static final int PRECISION_MAXIMALE = 255;

    public DerogationHorsPlanning {
        if (motif == null) {
            throw new SeanceRefusee(MotifDeRefus.MOTIF_DE_DEROGATION_REQUIS,
                    "Le motif est obligatoire pour confirmer une séance hors planning");
        }
        if (precision != null && precision.length() > PRECISION_MAXIMALE) {
            throw new SaisieInvalide("La précision du hors planning dépasse " + PRECISION_MAXIMALE + " caractères");
        }
        precision = precision == null || precision.isBlank() ? null : precision.trim();
        if (motif == MotifHorsPlanning.AUTRE && precision == null) {
            throw new SeanceRefusee(MotifDeRefus.PRECISION_DE_DEROGATION_REQUISE,
                    "Précisez le motif « autre » pour confirmer une séance hors planning");
        }
    }
}
