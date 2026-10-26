package com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse;

import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.util.UUID;

/**
 * Identités de fiches tirées au hasard (UUID v4), comme avant la refonte.
 */
public class NouveauxIdentifiantsAleatoires implements NouveauxIdentifiantsDInfirmier {
    @Override
    public InfirmierId suivant() {
        return new InfirmierId(UUID.randomUUID());
    }
}
