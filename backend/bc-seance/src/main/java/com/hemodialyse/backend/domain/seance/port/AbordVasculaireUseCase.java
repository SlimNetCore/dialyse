package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AbordVasculaireUseCase {
    List<AbordVasculaire> listByPatient(CenterId centerId, UUID patientId);

    /**
     * Historique paginé des abords du patient, du plus récent au plus ancien (AGENTS.md §9).
     */
    PagedResult<AbordVasculaire> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    AbordVasculaire save(CenterId centerId,
                         UUID patientId,
                         UUID abordId,
                         String typeAbord,
                         String cote,
                         String localisation,
                         LocalDate dateCreation,
                         LocalDate dateFin,
                         Boolean actif,
                         String complications);

    void delete(CenterId centerId, UUID patientId, UUID abordId);
}

