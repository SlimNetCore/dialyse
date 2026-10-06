package com.hemodialyse.backend.domain.planning.optimisation.model;

/**
 * Ce que l'optimisation planifie.
 * <ul>
 *   <li>{@code PATIENTS} : replace les patients (salle, créneau, générateur) pour minimiser les ressources ;</li>
 *   <li>{@code ROULEMENT} : conçoit le roulement hebdomadaire des infirmiers face aux placements actuels ;</li>
 *   <li>{@code COUVERTURE} : comble les cases en sous-effectif sur plusieurs semaines avec des remplaçants ;</li>
 *   <li>{@code COMPLET} : {@code PATIENTS} puis {@code ROULEMENT} sur les nouveaux placements.</li>
 * </ul>
 */
public enum PerimetreOptimisation {
    PATIENTS,
    ROULEMENT,
    COUVERTURE,
    COMPLET;

    public boolean placePatients() {
        return this == PATIENTS || this == COMPLET;
    }

    public boolean planifieRoulement() {
        return this == ROULEMENT || this == COMPLET;
    }

    public boolean planifieInfirmiers() {
        return this != PATIENTS;
    }
}
