package com.hemodialyse.backend.infrastructure.migration.port;

import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Port Out — suppression des données créées par un lot de reprise (annulation).
 */
public interface MigrationRollbackPort {

    /**
     * Données créées par le lot qui ne peuvent plus être supprimées parce qu'elles ont été utilisées depuis
     * (ex. patient ayant une prise en charge ou une séance). Vide = annulation possible.
     */
    List<String> blockers(CentreId centerId, List<IdMapping> created);

    /**
     * Supprime les données créées, dans l'ordre inverse de leurs dépendances.
     */
    void delete(CentreId centerId, List<IdMapping> created);

    /**
     * Remet dans leur état d'origine les données seulement rapprochées par le lot (assurés UPDATED ayant un état d'origine mémorisé).
     */
    void restore(CentreId centerId, List<IdMapping> updated);
}

