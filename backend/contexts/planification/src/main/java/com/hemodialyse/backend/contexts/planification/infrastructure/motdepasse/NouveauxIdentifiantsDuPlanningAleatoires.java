package com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse;

import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;

import java.util.UUID;

/**
 * Identités tirées au hasard (UUID v4), comme avant la refonte.
 */
public class NouveauxIdentifiantsDuPlanningAleatoires implements NouveauxIdentifiantsDuPlanning {
    @Override
    public AffectationId affectation() {
        return new AffectationId(UUID.randomUUID());
    }

    @Override
    public AbsenceId absence() {
        return new AbsenceId(UUID.randomUUID());
    }

    @Override
    public RemplacementId remplacement() {
        return new RemplacementId(UUID.randomUUID());
    }
}
