package com.hemodialyse.backend.domain.seance.model;

/**
 * Unité de la fréquence d'administration d'un traitement prescrit (EPO, fer injectable) — par
 * exemple « 5 » + {@code SEMAINE} pour « 5 doses par semaine ». Alimente le contrôle d'observance
 * (fenêtre glissante calculée à partir de cette unité).
 */
public enum UniteFrequence {
    HEURE,
    JOUR,
    SEMAINE,
    MOIS,
    ANNEE
}
