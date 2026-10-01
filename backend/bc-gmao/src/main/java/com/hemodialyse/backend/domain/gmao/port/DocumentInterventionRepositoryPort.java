package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.DocumentIntervention;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des pièces jointes d'intervention.
 */
public interface DocumentInterventionRepositoryPort {

    void save(DocumentIntervention document);

    /**
     * Page de métadonnées (sans contenu) des documents d'une intervention (AGENTS.md §9).
     */
    PagedResult<DocumentIntervention> findPagedByInterventionId(UUID interventionId, int page, int size);

    /**
     * Document complet (avec contenu) pour le téléchargement.
     */
    Optional<DocumentIntervention> findWithContenuById(UUID id);

    long countByInterventionId(UUID interventionId);

    void delete(UUID id);
}
