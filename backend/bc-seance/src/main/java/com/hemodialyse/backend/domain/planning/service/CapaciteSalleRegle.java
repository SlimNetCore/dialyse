package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

/**
 * Règle de capacité d'une salle : le nombre de générateurs affectés ne dépasse jamais la capacité fixée (sans capacité,
 * aucune limite).
 */
public final class CapaciteSalleRegle {

    private CapaciteSalleRegle() {
    }

    /**
     * @param capacite      capacité de la salle, {@code null} si illimitée
     * @param nbGenerateurs générateurs déjà affectés à la salle (sans celui qu'on affecte)
     * @throws BusinessException {@code SALLE_CAPACITE_DEPASSEE} si un générateur de plus dépasse la capacité
     */
    public static void verifierAffectation(Integer capacite, int nbGenerateurs, String salle) {
        if (capacite != null && nbGenerateurs + 1 > capacite) {
            throw new BusinessException("SALLE_CAPACITE_DEPASSEE",
                    "La salle « " + salle + " » est à sa capacité maximale (" + capacite + " générateur(s)) : "
                            + "l'affectation est refusée");
        }
    }
}
