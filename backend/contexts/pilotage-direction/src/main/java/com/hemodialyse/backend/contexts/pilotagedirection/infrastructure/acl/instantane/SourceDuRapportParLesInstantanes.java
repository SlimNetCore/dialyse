package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.instantane;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SourceDUnInstantane;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection.Instantane;

import java.util.UUID;

/**
 * Lit l'instantané mensuel par le langage publié du Pilotage (totaux bruts déjà masqués à la lecture) et le traduit en source du rapport
 * ({@link RapportDUnInstantane}). Le mois inconnu ou invalide est refusé par la lecture elle-même.
 */
public final class SourceDuRapportParLesInstantanes implements SourceDUnInstantane {

    private final InstantanesMensuelsDeLaDirection instantanes;

    public SourceDuRapportParLesInstantanes(InstantanesMensuelsDeLaDirection instantanes) {
        this.instantanes = instantanes;
    }

    @Override
    public SourceDUnMois source(UUID societeId, String mois) {
        Instantane instantane = instantanes.lire(societeId, mois);
        return new SourceDUnMois(instantane.mois(), RapportDUnInstantane.source("Rapport mensuel — " + instantane.mois(),
                instantane.generatedAt(), instantane.overview(), instantane.indicators(), instantane.breakdown()));
    }
}
