package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : la séquence de numérotation des factures du centre (F9, RG-FAC-010), une valeur par centre et par année. La
 * simulation la lit sans jamais la réserver ; seule l'émission réserve.
 */
public interface SequenceDeFacturation {

    /**
     * Dernière séquence consommée par le centre pour cette année ; 0 s'il n'a encore rien émis.
     */
    int courante(CentreId centre, int annee);

    /**
     * Réserve la séquence suivante du centre pour cette année et la renvoie (1 pour la première de l'année). La réservation est
     * atomique : deux émissions simultanées du même centre n'obtiennent jamais la même valeur, la seconde attend la fin de la
     * transaction de la première (KB-FAC-24). Elle est annulée avec la transaction qui échoue.
     */
    int reserverLaSuivante(CentreId centre, int annee);
}
