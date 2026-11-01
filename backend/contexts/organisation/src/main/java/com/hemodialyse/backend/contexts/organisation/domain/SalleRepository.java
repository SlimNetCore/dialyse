package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;

public interface SalleRepository {
    Optional<Salle> trouver(CentreId centreId, SalleId id);

    void sauvegarder(Salle salle);

    List<Salle> listerParCentre(CentreId centreId);
}
