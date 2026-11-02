package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;

public interface ProtocoleRepository {
    Optional<Protocole> trouver(ProtocoleId id);

    void sauvegarder(Protocole p);

    /** Supprime le protocole du centre : sans effet si le protocole appartient à un autre centre. */
    void supprimer(CentreId centreId, ProtocoleId id);

    PagedResult<Protocole> listerParCentre(CentreId centreId, int page, int size);
}
