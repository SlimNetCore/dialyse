package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SocieteRepository {
    Optional<Societe> trouver(SocieteId id);

    void sauvegarder(Societe societe);

    List<Societe> lister();

    PagedResult<Societe> listerParPage(String recherche, int page, int size);

    Optional<LogoDeSociete> lireLogo(SocieteId id);

    void enregistrerLogo(SocieteId id, LogoDeSociete logo);

    void supprimerLogo(SocieteId id);

    Set<SocieteId> idsAvecLogo(Collection<SocieteId> ids);
}
