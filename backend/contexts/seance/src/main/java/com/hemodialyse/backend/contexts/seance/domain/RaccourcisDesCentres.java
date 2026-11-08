package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : les raccourcis de consommables d'un centre (table {@code seance_raccourcis_articles}), un seul dépôt pour la racine
 * {@link RaccourcisDeConsommables}. La liste est remplacée en bloc.
 */
public interface RaccourcisDesCentres {

    /** Les raccourcis du centre, dans l'ordre d'affichage ; aucun raccourci : liste vide. */
    RaccourcisDeConsommables du(CentreId centre);

    /**
     * Remplace en bloc les raccourcis du centre. Deux remplacements simultanés du même centre se suivent ; verrou non obtenu dans le délai :
     * {@link EcritureConcurrente} (409).
     */
    void remplacer(RaccourcisDeConsommables raccourcis);
}
