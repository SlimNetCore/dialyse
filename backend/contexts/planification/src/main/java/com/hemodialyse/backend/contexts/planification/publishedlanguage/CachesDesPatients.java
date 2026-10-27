package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Port consommé par la Planification (slice P7), fourni par Patient et couverture (legacy) : les fiches lues en cache (fiche, liste,
 * comptages) doivent être rafraîchies quand une place est libérée. L'éviction a lieu APRÈS la validation de la transaction (jamais
 * avant : une lecture concurrente remettrait l'ancienne fiche en cache).
 */
public interface CachesDesPatients {

    void evincer(UUID centre, UUID patient);
}
