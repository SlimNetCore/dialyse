package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Ce que l'optimisation planifie (RG-PLN-080).
 * <ul>
 *   <li>{@code PATIENTS} : replace les patients (salle, créneau, générateur) pour minimiser les ressources ;</li>
 *   <li>{@code ROULEMENT} : conçoit le roulement hebdomadaire des infirmiers face aux placements actuels ;</li>
 *   <li>{@code COUVERTURE} : comble les cases en sous-effectif sur plusieurs semaines avec des remplaçants ;</li>
 *   <li>{@code COMPLET} : patients et roulement optimisés ensemble, dans un seul modèle ;</li>
 *   <li>{@code MAINTENANCE} : pour chaque séance datée dont le générateur est indisponible (intervention de maintenance),
 *   propose un déplacement temporaire sans toucher à la place habituelle du patient.</li>
 * </ul>
 */
public enum Perimetre {
    PATIENTS,
    ROULEMENT,
    COUVERTURE,
    COMPLET,
    MAINTENANCE;

    public boolean placeLesPatients() {
        return this == PATIENTS || this == COMPLET;
    }

    public boolean planifieLeRoulement() {
        return this == ROULEMENT || this == COMPLET;
    }

    public boolean planifieLesInfirmiers() {
        return this == ROULEMENT || this == COUVERTURE || this == COMPLET;
    }

    /** Patients et infirmiers dans un seul modèle. */
    public boolean conjoint() {
        return this == COMPLET;
    }

    /** Planification datée (fermetures, absences, maintenances réelles) sur plusieurs semaines, plutôt qu'une semaine type. */
    public boolean datee() {
        return this == COUVERTURE || this == MAINTENANCE;
    }
}
