package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SeanceUseCase {
    Seance create(CenterId centerId, UUID patientId, LocalDate dateSeance);

    Seance createFromQr(CenterId centerId, String qrCode);

    SeanceDetails getDetails(CenterId centerId, UUID seanceId);

    List<SeanceListItem> list(CenterId centerId);

    Seance updateDate(CenterId centerId, UUID seanceId, LocalDate dateSeance);

    Seance validate(CenterId centerId, UUID seanceId, String userId, List<SeanceArticleConsumption> consommations);

    Seance signByMedecin(CenterId centerId, UUID seanceId, String userId);

    /**
     * Remove a consommable from a validated seance: reverses the FEFO stock exits
     * for the given article, restores lot quantities and triggers PMP recalculation.
     */
    void removeConsommableSeance(CenterId centerId, UUID seanceId, UUID articleId, String userId);

    /**
     * Update the quantity of a consommable on a validated seance: reverses existing
     * exits then re-issues FEFO exits for the new quantity.
     */
    void updateConsommableSeance(CenterId centerId, UUID seanceId, UUID articleId,
                                 BigDecimal newQuantite, String userId);
}



