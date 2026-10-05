package com.hemodialyse.backend.domain.seance.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

/**
 * Value Object — confirmation explicite d'une séance hors planning : motif, précision (obligatoire pour « autre »)
 * et indication que l'auteur est administrateur (seul habilité à forcer un jour de fermeture du centre).
 */
public record DerogationPlanning(MotifHorsPlanning motif, String precision, boolean administrateur) {

    public DerogationPlanning {
        if (motif == null) {
            throw new BusinessException("SEANCE_DEROGATION_MOTIF_REQUIS",
                    "Le motif est obligatoire pour confirmer une séance hors planning");
        }
        precision = precision == null || precision.isBlank() ? null : precision.trim();
        if (motif == MotifHorsPlanning.AUTRE && precision == null) {
            throw new BusinessException("SEANCE_DEROGATION_PRECISION_REQUISE",
                    "Précisez le motif « autre » pour confirmer une séance hors planning");
        }
    }
}
