package com.hemodialyse.backend.contexts.stock.domain.consommations;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;

import java.util.UUID;

/**
 * Port sortant : le périmètre (salle, demi-journée) d'une séance, connu du contexte Séances (hors Stock). Implémenté par une ACL ;
 * périmètre vide (« journée ») si la séance n'a ni salle ni créneau reconnus.
 */
public interface PerimetreDesSeances {
    PerimetreDeSortie perimetreDe(CentreId centre, UUID seance);
}