package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeanceRepositoryPort {
    Seance save(Seance seance);
    Optional<Seance> findById(UUID seanceId, CenterId centerId);

    Optional<Seance> findByPatientIdAndDate(CenterId centerId, UUID patientId, LocalDate dateSeance);

    List<SeanceListItem> findAllByCenter(CenterId centerId);

    /**
     * Returns a paginated list of SeanceListItem (no patient enrichment — done by domain service).
     */
    PagedResult<SeanceListItem> findPagedByCenter(CenterId centerId, int page, int size);
}




