package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;

public interface MachineRepository {
    Optional<Machine> trouver(MachineId id);

    void sauvegarder(Machine m);

    void supprimer(MachineId id);

    PagedResult<Machine> listerParCentre(CentreId centreId, int page, int size);
}
