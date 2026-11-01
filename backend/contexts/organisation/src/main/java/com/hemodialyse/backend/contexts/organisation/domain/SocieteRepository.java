package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.util.List;
import java.util.Optional;

public interface SocieteRepository {
    Optional<Societe> trouver(SocieteId id);

    void sauvegarder(Societe societe);

    List<Societe> lister();
}
