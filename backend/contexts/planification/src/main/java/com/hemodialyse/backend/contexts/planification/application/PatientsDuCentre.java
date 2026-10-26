package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

/**
 * Port sortant vers Patient et couverture : la situation d'un patient dans le centre.
 */
public interface PatientsDuCentre {

    enum Situation {
        /** Inconnu, ou d'un autre centre. */
        INTROUVABLE,
        /** Suivi par le centre. */
        EN_SUIVI,
        /** Sorti : transféré, décédé, greffé ou guéri. */
        SORTI
    }

    Situation situation(CentreId centre, PatientId patient);
}
