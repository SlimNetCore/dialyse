package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

public interface DerogationRegularisationRepository {
    Optional<DerogationRegularisation> trouver(DerogationId id, CentreId centreId);

    Optional<DerogationRegularisation> trouverParSeance(SeanceId seanceId, CentreId centreId);

    void sauvegarder(DerogationRegularisation derogation);
}