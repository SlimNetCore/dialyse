package com.hemodialyse.backend.domain.medical.ordonnance.port;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrdonnanceUseCase {

    PagedResult<Ordonnance> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    Ordonnance create(CenterId centerId, UUID patientId, String medecinId, LocalDate datePrescription,
                      List<LigneOrdonnance> lignes);

    Ordonnance signer(CenterId centerId, UUID patientId, UUID ordonnanceId);

    Ordonnance marquerImprimee(CenterId centerId, UUID patientId, UUID ordonnanceId);

    Ordonnance annuler(CenterId centerId, UUID patientId, UUID ordonnanceId);
}
