package com.hemodialyse.backend.domain.pec.port;

import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port In — PEC use cases (domain-level).
 */
public interface PecUseCase {
    PriseEnCharge create(CenterId centerId, UUID patientId, LocalDate debutDemande, LocalDate finDemande, UUID forfaitDemandeId);
    PriseEnCharge validate(CenterId centerId, UUID pecId, LocalDate debutEffectif, LocalDate finEffectif, UUID forfaitEffectifId);
    PriseEnCharge close(CenterId centerId, UUID pecId);
    boolean canCreateSession(CenterId centerId, UUID pecId);
    List<PriseEnCharge> listByPatient(CenterId centerId, UUID patientId);
    List<PriseEnCharge> listByCenter(CenterId centerId);
}

