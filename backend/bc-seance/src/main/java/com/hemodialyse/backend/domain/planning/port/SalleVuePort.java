package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.SalleVue;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de lecture des salles d'un centre avec leurs générateurs affectés et leur capacité.
 */
public interface SalleVuePort {

    PagedResult<SalleVue> findPaged(UUID centerId, int page, int size);

    Optional<SalleVue> findById(UUID centerId, UUID salleId);
}
