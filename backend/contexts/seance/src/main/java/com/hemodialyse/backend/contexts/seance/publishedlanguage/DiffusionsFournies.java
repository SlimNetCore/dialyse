package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : les diffusions temps réel aux écrans du centre. Appelées après la
 * validation de l'unité de travail ; un échec de diffusion ne défait jamais la séance. {@code jour} est le texte ISO de la date.
 */
public interface DiffusionsFournies {

    void seanceCreee(UUID centre, UUID seance, UUID patient, String nom, String prenom, String jour);

    void seanceValidee(UUID centre, UUID seance, UUID patient, String nom, String prenom, String jour);

    /** {@code type} vaut {@code SEANCE_CREEE} ou {@code SEANCE_VALIDEE}. */
    void saisieDeLInfirmier(UUID centre, String type, UUID patient, String nom, String prenom, String auteur, String jour);

    /** SE2 : une séance d'un jour passé vient d'être déverrouillée pour régularisation. */
    void seanceDeverrouillee(UUID centre, UUID seance, String nom, String prenom, String jour);
}
