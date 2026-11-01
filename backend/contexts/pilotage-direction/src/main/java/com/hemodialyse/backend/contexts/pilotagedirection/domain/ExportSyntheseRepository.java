package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * Port de sortie de l'entité {@code ExportSynthese}. Seule la couche d'infrastructure fournit
 * une implémentation. Toutes les requêtes sont filtrées par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface ExportSyntheseRepository {

    void save(ExportSynthese export);

    Optional<ExportSynthese> trouver(ExportSyntheseId id, CentreId centreId);
}
