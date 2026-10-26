package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.infirmier.model.Infirmier;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des fiches d'infirmiers pour le roulement, les absences, les remplacements et « Mon planning » (slices P3 et P4,
 * toujours bornée au centre — AGENTS.md §2). Les écritures du référentiel sont servies par le contexte Planification (slice P2).
 */
public interface InfirmierRepositoryPort {

    Optional<Infirmier> findById(UUID centerId, UUID id);

    /**
     * Fiche reliée à un compte utilisateur, dans un centre.
     */
    Optional<Infirmier> findByUserId(UUID centerId, UUID userId);
}
