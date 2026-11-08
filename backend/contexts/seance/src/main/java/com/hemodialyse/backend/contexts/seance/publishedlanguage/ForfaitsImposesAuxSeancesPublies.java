package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0099, SE7e) : l'usage d'un forfait du référentiel comme forfait imposé à des séances,
 * lu par l'administration des référentiels pour refuser la suppression d'un forfait utilisé ({@code REFERENTIAL_IN_USE}). Lecture seule.
 */
public interface ForfaitsImposesAuxSeancesPublies {

    /**
     * Le nombre de séances qui portent ce forfait imposé, de N'IMPORTE QUEL centre et de N'IMPORTE QUEL statut (ANNULEE comprise) :
     * KB-SEA-91, reproduit (E17).
     */
    long nombreDeSeancesQuiImposentLeForfait(UUID forfait);
}
