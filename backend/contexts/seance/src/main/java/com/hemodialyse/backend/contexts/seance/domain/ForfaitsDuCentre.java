package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : le référentiel des forfaits du centre (propriété du référentiel). Inconnu ou d'un autre centre : vide. */
public interface ForfaitsDuCentre {
    Optional<ForfaitDuCentre> trouver(CentreId centre, ForfaitId forfait);
}
