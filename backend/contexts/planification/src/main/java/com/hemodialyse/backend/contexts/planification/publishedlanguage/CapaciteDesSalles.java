package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (P9, RG-PLN-061, ADR 0069) : la capacité des salles, consultée par GMAO avant d'affecter un générateur à une salle.
 * Sans salle, rien à contrôler ; une salle sans capacité, inconnue ou d'un autre centre n'impose aucune limite. Lecture seule.
 */
public interface CapaciteDesSalles {

    /**
     * @param equipement le générateur déjà affecté à la salle (ignoré dans le décompte), {@code null} à l'enregistrement
     * @throws CapaciteDeLaSalleDepassee si un générateur de plus dépasserait la capacité de la salle
     */
    void verifierAffectation(UUID centre, UUID salle, UUID equipement);

    /** Refus : la salle est à sa capacité maximale (RG-PLN-061). */
    class CapaciteDeLaSalleDepassee extends RuntimeException {
        private final String salle;
        private final int capacite;

        public CapaciteDeLaSalleDepassee(String salle, int capacite) {
            super("La salle « " + salle + " » est à sa capacité maximale (" + capacite + " générateur(s)) : "
                    + "l'affectation est refusée");
            this.salle = salle;
            this.capacite = capacite;
        }

        public String salle() {
            return salle;
        }

        public int capacite() {
            return capacite;
        }
    }
}
