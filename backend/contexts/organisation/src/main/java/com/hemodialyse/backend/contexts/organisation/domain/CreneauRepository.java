package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;

public interface CreneauRepository {
    Optional<Creneau> trouver(CentreId centreId, CreneauId id);

    void sauvegarder(Creneau creneau);

    List<Creneau> listerParCentre(CentreId centreId);
}
