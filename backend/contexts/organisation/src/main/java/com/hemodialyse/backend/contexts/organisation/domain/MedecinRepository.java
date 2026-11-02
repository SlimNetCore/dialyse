package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;

public interface MedecinRepository {
    Optional<Medecin> trouver(MedecinId id);

    void sauvegarder(Medecin m);

    /** Médecins actifs du centre (les médecins désactivés ne sont plus proposés). */
    PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size);
}
