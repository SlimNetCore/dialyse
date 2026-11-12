package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant : les séances d'un patient imprimables dans son cahier (validées, signées ou facturées, dans la période). */
public interface SeancesDuCahier {

    int nombreAImprimer(CentreId centre, PatientId patient, PeriodeDuCahier periode);
}
