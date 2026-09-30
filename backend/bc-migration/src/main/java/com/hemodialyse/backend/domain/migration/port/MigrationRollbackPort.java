package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;

/**
 * Port Out — suppression des données créées par un lot de reprise (annulation).
 */
public interface MigrationRollbackPort {

    /**
     * Données créées par le lot qui ne peuvent plus être supprimées parce qu'elles ont été utilisées depuis
     * (ex. patient ayant une prise en charge ou une séance). Vide = annulation possible.
     */
    List<String> blockers(CenterId centerId, List<IdMapping> created);

    /**
     * Supprime les données créées, dans l'ordre inverse de leurs dépendances.
     */
    void delete(CenterId centerId, List<IdMapping> created);
}

