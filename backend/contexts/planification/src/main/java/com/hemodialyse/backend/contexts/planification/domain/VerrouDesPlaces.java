package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


/**
 * Port sortant : le verrou du centre pris par toute écriture de place (ADR 0049, décision du 27/10). Contrairement à
 * {@link VerrouDuCentre} (paramétrage, ADR 0055), il <b>crée la ligne {@code planning_parametres} par défaut si elle est
 * absente</b>, puis la verrouille (FOR UPDATE, {@code lock_timeout} 5 s) : un centre jamais paramétré a ainsi une exclusion mutuelle.
 * Tenu jusqu'à la fin de la transaction de l'appelant ; s'il n'est pas obtenu, {@link EcritureConcurrente} (409
 * {@code PLANIFICATION_ECRITURE_CONCURRENTE}). Ordre global des verrous : centre, puis fiches des infirmiers, puis comptes.
 */
public interface VerrouDesPlaces {

    void prendre(CentreId centre);
}
