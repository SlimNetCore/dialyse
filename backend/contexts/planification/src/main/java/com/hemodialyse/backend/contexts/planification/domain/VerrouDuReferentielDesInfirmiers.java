package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


/**
 * Port sortant : exclusion mutuelle des écritures du référentiel des infirmiers d'un centre (ADR 0056), prise en premier par
 * chaque écriture et tenue jusqu'à la fin de la transaction. Elle protège les règles d'ensemble que la base ne garantit pas :
 * unicité du matricule sans tenir compte de la casse, un compte lié une seule fois. Une écriture qui ne l'obtient pas dans le
 * délai de la transaction reçoit {@link EcritureConcurrente}.
 * <p>
 * Différence avec {@link VerrouDuCentre} (paramétrage, ADR 0055) : la ligne {@code planning_parametres} peut ne pas exister ;
 * ce verrou porte sur la ligne du centre lui-même, qui existe toujours.
 */
public interface VerrouDuReferentielDesInfirmiers {
    void prendre(CentreId centre);
}
