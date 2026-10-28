package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (lecture) : les forfaits du référentiel du centre, que l'on peut imposer à une séance. Un forfait d'un autre
 * centre est inconnu.
 */
public interface ForfaitsDuCentre {

    Optional<ForfaitApplique> forfait(CentreId centre, UUID forfait);
}
