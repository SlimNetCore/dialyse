package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeanceRepositoryPort {
    Seance save(Seance seance);

    // SE6 (ADR 0093) : la suppression vit dans contexts/seance (Seances.supprimer).
    Optional<Seance> findById(UUID seanceId, CenterId centerId);

    Optional<Seance> findByPatientIdAndDate(CenterId centerId, UUID patientId, LocalDate dateSeance);

    /**
     * Historique des séances du centre : filtré, trié et paginé en base, avec le code, le nom et le prénom du patient
     * déjà renseignés (jointure, sans requête par ligne).
     */
    PagedResult<SeanceListItem> search(CenterId centerId, SeanceSearch criteria, int page, int size);
}




