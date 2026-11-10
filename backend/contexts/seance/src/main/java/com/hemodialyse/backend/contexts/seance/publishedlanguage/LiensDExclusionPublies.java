package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0098, SE7d) : le lien qu'une séance exclue de la facturation garde vers l'absence
 * déclarée par l'exclusion ({@code exclusion_absence_id}, V39). Lecture seule.
 */
public interface LiensDExclusionPublies {

    /**
     * Vrai si au moins une séance du centre au statut {@code ABSENT} porte un lien vers cette absence ; le lien résiduel d'une séance
     * d'un autre statut est ignoré (KB-SEA-81 corrigé le 2026-11-10, ADR 0117), de même que celui d'une séance d'un autre centre.
     */
    boolean absenceLiee(UUID centre, UUID absence);
}
