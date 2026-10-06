package com.hemodialyse.backend.domain.planning.optimisation.model;

/**
 * Ce que l'optimisation planifie.
 * <ul>
 *   <li>{@code PATIENTS} : replace les patients (salle, créneau, générateur) pour minimiser les ressources ;</li>
 *   <li>{@code ROULEMENT} : conçoit le roulement hebdomadaire des infirmiers face aux placements actuels ;</li>
 *   <li>{@code COUVERTURE} : comble les cases en sous-effectif sur plusieurs semaines avec des remplaçants ;</li>
 *   <li>{@code COMPLET} : patients et roulement optimisés ensemble, dans un seul modèle (le besoin en infirmiers suit
 *   les placements pendant le calcul) ;</li>
 *   <li>{@code MAINTENANCE} : pour chaque séance datée dont le générateur est indisponible (intervention GMAO),
 *   propose un déplacement temporaire sans toucher à la place habituelle du patient.</li>
 * </ul>
 */
public enum PerimetreOptimisation {
    PATIENTS,
    ROULEMENT,
    COUVERTURE,
    COMPLET,
    MAINTENANCE;

    public boolean placePatients() {
        return this == PATIENTS || this == COMPLET;
    }

    public boolean planifieRoulement() {
        return this == ROULEMENT || this == COMPLET;
    }

    public boolean planifieInfirmiers() {
        return this == ROULEMENT || this == COUVERTURE || this == COMPLET;
    }

    /**
     * Patients et infirmiers dans un seul modèle.
     */
    public boolean conjoint() {
        return this == COMPLET;
    }

    /**
     * Planification datée (fermetures, absences, maintenances réelles) sur 1 à 4 semaines, plutôt qu'une semaine type.
     */
    public boolean datee() {
        return this == COUVERTURE || this == MAINTENANCE;
    }
}
