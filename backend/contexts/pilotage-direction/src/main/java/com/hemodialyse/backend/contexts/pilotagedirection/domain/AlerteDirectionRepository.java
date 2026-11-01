package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port de sortie de l'agrégat {@code AlerteDirection}. Seule la couche d'infrastructure
 * fournit une implémentation. Toutes les requêtes sont filtrées par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface AlerteDirectionRepository {

    void save(AlerteDirection alerte);

    Optional<AlerteDirection> trouver(AlerteDirectionId id, CentreId centreId);

    boolean existeParCentreEtJour(CentreId centreId, LocalDate date);

    PagedResult<AlerteDirection> listerParCentre(CentreId centreId, int page, int size);
}
