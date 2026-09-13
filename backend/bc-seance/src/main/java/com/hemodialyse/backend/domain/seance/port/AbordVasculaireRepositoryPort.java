package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

public interface AbordVasculaireRepositoryPort {
    List<AbordVasculaire> findByPatientId(UUID patientId, CenterId centerId);

    /**
     * Variante paginée, du plus récent au plus ancien (AGENTS.md §9).
     */
    PagedResult<AbordVasculaire> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    AbordVasculaire save(AbordVasculaire abord);

    void deleteById(UUID abordId, UUID patientId, CenterId centerId);
}

