package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Règle de capacité d'une salle (RG-PLN-061) : le nombre de générateurs affectés ne dépasse jamais la capacité fixée ; sans capacité,
 * aucune limite.
 */
public final class CapaciteDeLaSalle {

    private CapaciteDeLaSalle() {
    }

    /**
     * @param capacite      capacité de la salle, {@code null} si illimitée
     * @param nbGenerateurs générateurs déjà affectés à la salle (sans celui qu'on affecte)
     */
    public static boolean depasseraitAvecUnGenerateurDePlus(Integer capacite, int nbGenerateurs) {
        return capacite != null && nbGenerateurs + 1 > capacite;
    }
}
