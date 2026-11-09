package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels.InstantaneFige;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;

import java.util.UUID;

/**
 * Relit l'instantané figé d'un mois, tel quel. Aucun contrôle du futur en lecture : un mois futur est simplement introuvable
 * ({@code SNAPSHOT_INTROUVABLE}, KB-DIR-06 b, reproduit).
 */
public final class ConsulterUnInstantane {

    private final InstantanesMensuels instantanes;

    public ConsulterUnInstantane(InstantanesMensuels instantanes) {
        this.instantanes = instantanes;
    }

    public InstantaneFige execute(UUID societeId, String mois) {
        MoisDInstantane lu = MoisDInstantane.lire(mois);
        return instantanes.trouver(societeId, lu)
                .orElseThrow(() -> new RegleDuPilotageViolee("SNAPSHOT_INTROUVABLE", "Aucun instantané pour ce mois"));
    }
}
