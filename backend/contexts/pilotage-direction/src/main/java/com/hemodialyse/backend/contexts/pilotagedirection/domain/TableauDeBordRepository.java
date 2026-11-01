package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * Port de sortie du read model {@code TableauDeBord}. Seule la couche d'infrastructure fournit
 * une implémentation. Toutes les requêtes sont filtrées par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface TableauDeBordRepository {

    void save(TableauDeBord tableau);

    Optional<TableauDeBord> trouver(CentreId centreId);
}
