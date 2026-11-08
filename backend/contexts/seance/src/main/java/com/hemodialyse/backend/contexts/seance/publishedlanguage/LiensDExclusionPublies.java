package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0098, SE7d) : le lien qu'une séance exclue de la facturation garde vers l'absence
 * déclarée par l'exclusion ({@code exclusion_absence_id}, V39). Lecture seule.
 */
public interface LiensDExclusionPublies {

    /**
     * Vrai si au moins une séance du centre porte un lien vers cette absence, QUEL QUE SOIT son statut (KB-SEA-81, reproduit) ; le lien
     * porté par une séance d'un autre centre est ignoré.
     */
    boolean absenceLiee(UUID centre, UUID absence);
}
