package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.SituationPlanning;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port de lecture du planning d'un patient (jours de dialyse, calendrier du centre, état du patient).
 */
public interface SeancePlanningPort {

    SituationPlanning situation(CenterId centerId, UUID patientId, LocalDate date);
}
