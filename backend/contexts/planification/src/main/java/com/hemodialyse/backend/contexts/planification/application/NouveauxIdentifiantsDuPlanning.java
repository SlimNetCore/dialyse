package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;

/**
 * Port sortant : les identifiants des nouvelles affectations, absences et remplacements (le domaine ne tire pas d'aléa).
 */
public interface NouveauxIdentifiantsDuPlanning {

    AffectationId affectation();

    AbsenceId absence();

    RemplacementId remplacement();
}
