package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;

/**
 * Politique de démarrage du serveur (P20) : un calcul encore {@code EN_COURS} a été interrompu avec l'ancien processus ; il échoue
 * avec un motif lisible, pour ne pas bloquer le centre (« une seule en cours »).
 */
public class ReprendreLesExecutionsInterrompues {

    public static final String MOTIF = "Calcul interrompu par l'arrêt du serveur";

    private final UniteDeTravail uniteDeTravail;
    private final ExecutionsRepository executions;

    public ReprendreLesExecutionsInterrompues(UniteDeTravail uniteDeTravail, ExecutionsRepository executions) {
        this.uniteDeTravail = uniteDeTravail;
        this.executions = executions;
    }

    /** @return le nombre d'exécutions interrompues */
    public int executer() {
        return uniteDeTravail.executer(() -> executions.interrompreLesEnCours(MOTIF));
    }
}
