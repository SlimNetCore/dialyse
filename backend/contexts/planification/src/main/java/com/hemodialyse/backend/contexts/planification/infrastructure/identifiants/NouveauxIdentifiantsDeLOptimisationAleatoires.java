package com.hemodialyse.backend.contexts.planification.infrastructure.identifiants;

import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;

import java.util.UUID;

/**
 * Identités tirées au hasard (UUID v4), comme avant la refonte.
 */
public class NouveauxIdentifiantsDeLOptimisationAleatoires implements NouveauxIdentifiantsDeLOptimisation {

    @Override
    public ExecutionId execution() {
        return new ExecutionId(UUID.randomUUID());
    }

    @Override
    public DeplacementTemporaireId deplacementTemporaire() {
        return new DeplacementTemporaireId(UUID.randomUUID());
    }

    @Override
    public AffectationId affectation() {
        return new AffectationId(UUID.randomUUID());
    }

    @Override
    public RemplacementId remplacement() {
        return new RemplacementId(UUID.randomUUID());
    }
}
