package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) de persistance pour le référentiel Intervenant GMAO.
 */
public interface IntervenantRepositoryPort {

    Intervenant save(Intervenant intervenant);

    Optional<Intervenant> findById(UUID id);

    /**
     * Liste paginée des intervenants d'un centre (AGENTS.md §9).
     */
    PagedResult<Intervenant> findPaged(UUID centreId, int page, int size);
}
