package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : la validation de la séance par l'infirmier (slice SE2, encore
 * legacy : statut, mémorisation de la place, signature). Participe à la transaction de l'appelant ; sans effet si la séance est déjà
 * validée. Retiré quand SE2 sera reprise.
 */
public interface ValidationFournie {

    void validerParLInfirmier(UUID centre, UUID seance, String auteur);
}
