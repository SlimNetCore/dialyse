package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;

/**
 * Port sortant : les identifiants des nouvelles exécutions, des nouveaux déplacements temporaires et des affectations et
 * remplacements créés par l'application d'une proposition (le domaine ne tire pas d'aléa).
 */
public interface NouveauxIdentifiantsDeLOptimisation {
    ExecutionId execution();

    DeplacementTemporaireId deplacementTemporaire();

    AffectationId affectation();

    RemplacementId remplacement();
}
