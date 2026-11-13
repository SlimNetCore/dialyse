package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.List;
import java.util.Optional;

public interface MedecinRepository {
    Optional<Medecin> trouver(MedecinId id);

    void sauvegarder(Medecin m);

    /** Médecins actifs du centre (les médecins désactivés ne sont plus proposés). */
    PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size);

    /** PL11-3 : tous les médecins du centre, désactivés compris (réactivation d'un nom saisi à nouveau, D6). */
    List<Medecin> listerTousParCentre(CentreId centreId);

    /** PL11-3 : une page des médecins proposés, filtrés par la recherche (nom, prénom ou spécialité, casse ignorée), triés par nom puis prénom. */
    PageDeMedecins page(CentreId centreId, String recherche, int page, int taille);

    record PageDeMedecins(List<Medecin> lignes, long total) {
    }
}
