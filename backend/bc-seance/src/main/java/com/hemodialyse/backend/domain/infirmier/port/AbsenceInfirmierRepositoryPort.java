package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des absences des infirmiers.
 */
public interface AbsenceInfirmierRepositoryPort {

    AbsenceInfirmier save(AbsenceInfirmier absence);

    Optional<AbsenceInfirmier> findById(UUID centerId, UUID id);

    /**
     * Liste paginée des absences d'un centre, des plus récentes aux plus anciennes (AGENTS.md §9).
     */
    PagedResult<AbsenceInfirmier> findPaged(UUID centerId, int page, int size);

    /**
     * Liste paginée des absences d'un infirmier, des plus récentes aux plus anciennes.
     */
    PagedResult<AbsenceInfirmier> findPagedByInfirmier(UUID centerId, UUID infirmierId, int page, int size);

    void delete(UUID centerId, UUID id);
}
