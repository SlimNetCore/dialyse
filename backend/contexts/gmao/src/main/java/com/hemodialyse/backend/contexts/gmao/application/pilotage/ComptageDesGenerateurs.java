package com.hemodialyse.backend.contexts.gmao.application.pilotage;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursInstalles;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Nombre de générateurs installés par centre, publié au pilotage de la direction (capacité théorique). */
public final class ComptageDesGenerateurs implements GenerateursInstalles {

    private final LectureDesComptages comptages;

    public ComptageDesGenerateurs(LectureDesComptages comptages) {
        this.comptages = comptages;
    }

    @Override
    public Map<UUID, Long> parCentre(Collection<UUID> centres, Instant avant) {
        return centres.isEmpty() ? Map.of() : comptages.generateursInstallesAvant(centres, avant);
    }
}
