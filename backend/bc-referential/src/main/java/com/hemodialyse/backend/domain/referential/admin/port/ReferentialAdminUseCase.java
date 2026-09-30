package com.hemodialyse.backend.domain.referential.admin.port;

import com.hemodialyse.backend.domain.referential.admin.model.ImportReport;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Map;
import java.util.UUID;

/**
 * Port In — administration des référentiels d'un centre (saisie unitaire et import de fichier).
 */
public interface ReferentialAdminUseCase {

    PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size);

    ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values);

    ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values);

    /**
     * Refusé si la ligne est encore utilisée (patients, PEC, séances, autre référentiel…).
     */
    void delete(CenterId centerId, ReferentialKind kind, UUID id);

    /**
     * Vérifie un fichier et, si {@code dryRun} est faux et que le fichier est entièrement valide, l'importe :
     * création des nouvelles lignes, mise à jour de celles reconnues par leur clé (tout ou rien).
     */
    ImportReport importEntries(CenterId centerId, ReferentialKind kind, ImportTable table, boolean dryRun);
}

