package com.hemodialyse.backend.contexts.absencespatients.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

/**
 * Port sortant : existence d'un patient dans le centre (contexte Patient et couverture, SQL direct tant qu'il n'est pas extrait).
 */
public interface PatientsDuCentre {

    boolean existe(CentreId centre, PatientId patient);
}
