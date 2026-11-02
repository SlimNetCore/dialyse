package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;
import java.util.UUID;

public interface MedecinRepository {
    Optional<Medecin> trouver(MedecinId id);

    void sauvegarder(Medecin m);

    /** Supprime le médecin du centre : sans effet si le médecin appartient à un autre centre. */
    void supprimer(CentreId centreId, MedecinId id);

    PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size);
}
