package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

/**
 * Port sortant : verrou de la société, tenu jusqu'à la fin de la transaction en cours. Sérialise les écritures qui touchent à
 * l'ensemble des centres d'une société (invariant « au moins un centre actif »). Un verrou qu'on ne peut pas prendre dans le délai
 * devient une {@link EcritureConcurrente}.
 */
public interface VerrouDeLaSociete {

    void prendre(SocieteId societeId);

    /** Aucun verrou : tests d'application avec des fakes en mémoire. */
    VerrouDeLaSociete AUCUN = societeId -> {
    };
}
