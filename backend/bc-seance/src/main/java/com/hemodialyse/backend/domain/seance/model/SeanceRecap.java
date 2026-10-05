package com.hemodialyse.backend.domain.seance.model;

/**
 * Séance passée d'un patient avec ses constantes paramédicales (si elles ont été saisies) : le rappel affiché à
 * l'infirmier pendant la séance en cours.
 */
public record SeanceRecap(Seance seance, VoletParamedical voletParamedical) {
}
