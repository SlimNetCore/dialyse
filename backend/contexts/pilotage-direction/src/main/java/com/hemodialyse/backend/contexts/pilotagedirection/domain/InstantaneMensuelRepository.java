package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;

/**
 * Port de sortie de l'agrégat {@code InstantaneMensuel}. Seule la couche d'infrastructure
 * fournit une implémentation. Toutes les requêtes sont filtrées par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface InstantaneMensuelRepository {

    void save(InstantaneMensuel instantane);

    Optional<InstantaneMensuel> trouver(InstantaneMensuelId id, CentreId centreId);

    boolean existeParCentreEtMois(CentreId centreId, Mois mois);

    PagedResult<InstantaneMensuel> listerParCentre(CentreId centreId, int page, int size);
}
