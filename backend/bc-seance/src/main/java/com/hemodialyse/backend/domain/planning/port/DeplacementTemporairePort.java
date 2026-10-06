package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port de persistance des déplacements temporaires de séances (toujours borné au centre — AGENTS.md §2).
 */
public interface DeplacementTemporairePort {

    /**
     * Déplacements dont la date est comprise entre deux dates (bornes incluses).
     */
    List<DeplacementTemporaire> entre(UUID centerId, LocalDate du, LocalDate au);

    /**
     * Enregistre les déplacements ; un déplacement existant pour le même patient et la même date est remplacé.
     */
    void enregistrer(List<DeplacementTemporaire> deplacements);
}
