package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;

/**
 * Port sortant : les identifiants des nouvelles exécutions et des nouveaux déplacements temporaires, en plus de ceux des
 * affectations et des remplacements créés par l'application d'une proposition (le domaine ne tire pas d'aléa).
 */
public interface NouveauxIdentifiantsDeLOptimisation extends NouveauxIdentifiantsDuPlanning {
    ExecutionId execution();

    DeplacementTemporaireId deplacementTemporaire();
}
