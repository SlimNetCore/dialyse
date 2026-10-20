package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

/**
 * Port sortant (lecture) : la séquence de numérotation des factures du centre. La simulation la lit sans jamais la réserver.
 */
public interface SequenceDeFacturation {

    /**
     * Dernière séquence consommée par le centre pour cette année ; 0 s'il n'a encore rien émis.
     */
    int courante(CentreId centre, int annee);
}
