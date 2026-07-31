package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

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
}



