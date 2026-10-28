package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

import java.util.Collection;
import java.util.Map;

/** Port sortant vers Patient et couverture : le nom affichable (« NOM Prénom ») des patients d'un centre, pour nommer les alertes. */
public interface NomsDesPatients {

    /** Les patients inconnus ou d'un autre centre sont absents du résultat. */
    Map<PatientId, String> de(CentreId centre, Collection<PatientId> patients);
}
