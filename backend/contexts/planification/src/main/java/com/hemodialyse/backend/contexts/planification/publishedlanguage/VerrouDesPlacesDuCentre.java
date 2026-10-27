package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (écriture) : le verrou qui protège les places d'un centre. L'application d'une proposition d'optimisation (P8, encore dans
 * le legacy) écrit les places des patients sans passer par {@link PlacementDesPatients} : elle prend ce verrou, <b>dans sa propre
 * transaction</b> et avant de lire l'état à contrôler, pour s'exclure mutuellement avec l'enregistrement d'une fiche ou d'une sérologie.
 * Ordre global des verrous (ADR 0049, 0059) : centre, puis fiches des infirmiers, puis lignes propres.
 * <p>
 * La ligne de paramètres du centre est créée avec ses valeurs par défaut si elle manque. Le délai d'attente est de 5 s.
 *
 * @throws PlacementDesPatients.EcritureConcurrente le verrou n'est pas obtenu dans le délai (409 {@code PLANIFICATION_ECRITURE_CONCURRENTE})
 */
public interface VerrouDesPlacesDuCentre {

    void verrouiller(UUID centre);
}
