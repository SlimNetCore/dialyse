package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primary port: bon de sortie (BS) using FEFO, linked to a hemodialysis seance.
 */
public interface BonSortieUseCase {
    BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                     LocalDate dateSortie, List<SortieRequestItem> items, String userId);

    BonSortie get(CenterId centerId, UUID bonId);

    List<BonSortie> list(CenterId centerId);
}

