package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié des Absences de patients, côté FOURNI (ADR 0098, SE7d) : le lien qu'une séance exclue de la facturation garde vers
 * l'absence déclarée par l'exclusion, fourni par la plateforme à partir de {@code seance.v1}. Lecture seule.
 */
public interface LiensDExclusionFournis {

    /** Vrai si une séance du centre porte un lien vers cette absence, quel que soit son statut (KB-SEA-81). */
    boolean absenceLiee(UUID centre, UUID absence);
}
