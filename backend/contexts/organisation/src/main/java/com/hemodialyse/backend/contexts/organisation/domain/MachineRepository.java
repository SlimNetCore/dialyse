package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;

public interface MachineRepository {
    Optional<Machine> trouver(MachineId id);

    void sauvegarder(Machine m);

    /** Supprime la machine du centre : sans effet si la machine appartient à un autre centre. */
    void supprimer(CentreId centreId, MachineId id);

    PagedResult<Machine> listerParCentre(CentreId centreId, int page, int size);
}
