package com.hemodialyse.backend.domain.medical.anemie.port;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

public interface AdministrationTraitementRepositoryPort {

    PagedResult<AdministrationTraitement> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    List<AdministrationTraitement> findByPatientId(UUID patientId, CenterId centerId);

    AdministrationTraitement save(AdministrationTraitement administration);

    /**
     * Supprime les administrations faites pendant une séance (séance supprimée).
     */
    void deleteBySeanceId(UUID seanceId, CenterId centerId);
}
