package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

/**
 * Données du centre dont les contraintes ont besoin : ratio de sécurité infirmiers / patients, générateurs en service et
 * générateurs de secours à garder libres (1 pour 8).
 */
public record ContexteCentre(int patientsParInfirmier, int generateurs, int secours) {

    /**
     * Générateurs qui peuvent servir un patient au même créneau le même jour tout en gardant la réserve de secours.
     */
    public int generateursExploitables() {
        return Math.max(0, generateurs - secours);
    }
}
