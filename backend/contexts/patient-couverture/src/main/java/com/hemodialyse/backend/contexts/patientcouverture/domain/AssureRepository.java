package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

public interface AssureRepository {
    Optional<Assure> trouver(AssureId id, CentreId centreId);

    void sauvegarder(Assure a);
}