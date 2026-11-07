package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/** Port sortant : les faits du planning d'un patient à une date (jours de dialyse, fermetures du centre, état du patient). */
public interface PlanningDesPatients {
    SituationDuPlanning situation(CentreId centre, PatientId patient, LocalDate jour);
}
