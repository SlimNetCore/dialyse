package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

/**
 * Port sortant : la situation d'un infirmier dans le centre (référentiel des infirmiers, encore dans le legacy).
 */
public interface InfirmiersDuCentre {

    enum Situation {
        /** Inconnu, ou d'un autre centre. */
        INTROUVABLE,
        ACTIF,
        INACTIF
    }

    Situation situation(CentreId centre, InfirmierId infirmier);
}
