package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.util.List;
import java.util.Optional;

public interface CentreRepository {
    Optional<Centre> trouver(CentreId id);

    void sauvegarder(Centre centre);

    List<Centre> listerActifs(SocieteId societeId);

    List<Centre> listerParSociete(SocieteId societeId);
}
