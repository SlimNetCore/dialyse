package com.hemodialyse.backend.domain.seance.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

/**
 * Motif obligatoire de la suppression d'une séance (erreur de saisie, doublon, mauvais patient…), conservé au journal.
 */
public record MotifSuppressionSeance(String valeur) {

    public static final int LONGUEUR_MIN = 5;
    public static final int LONGUEUR_MAX = 500;

    public MotifSuppressionSeance {
        valeur = valeur == null ? "" : valeur.trim();
        if (valeur.length() < LONGUEUR_MIN || valeur.length() > LONGUEUR_MAX) {
            throw new BusinessException("SEANCE_SUPPRESSION_MOTIF_INVALIDE", "Le motif de suppression doit compter de "
                    + LONGUEUR_MIN + " à " + LONGUEUR_MAX + " caractères");
        }
    }
}
