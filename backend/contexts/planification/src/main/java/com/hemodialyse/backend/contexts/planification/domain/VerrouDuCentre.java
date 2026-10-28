package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


/**
 * Port sortant : le verrou du centre (ADR 0049), pris par toute écriture de paramétrage. C'est le verrou de la ligne
 * {@code planning_parametres} du centre, tenu jusqu'à la fin de la transaction de l'écriture. Une écriture qui ne l'obtient pas
 * dans le délai de la transaction reçoit {@link EcritureConcurrente}. Tant que le centre n'a pas de ligne, il n'y a rien à
 * verrouiller (et rien n'est créé) : les deux premières écritures simultanées d'une même clé sont départagées par la clé
 * primaire, la perdante reçoit aussi {@link EcritureConcurrente}.
 * <p>
 * <b>Valable pour le paramétrage seulement</b> (ADR 0055) : {@code PlacesDuCentre} devra créer la ligne par défaut ou verrouiller
 * autrement, faute de quoi un centre jamais paramétré n'aurait aucune exclusion mutuelle. Le verrou du centre est toujours pris en premier.
 */
public interface VerrouDuCentre {

    void prendre(CentreId centre);
}
