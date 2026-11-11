package com.hemodialyse.backend.infrastructure.referential.admin.port;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportReport;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Map;
import java.util.UUID;

/**
 * Port In — administration des référentiels d'un centre (saisie unitaire et import de fichier).
 */
public interface ReferentialAdminUseCase {

    PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size);

    ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values);

    ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values);

    /**
     * Refusé si la ligne est encore utilisée (patients, PEC, séances, autre référentiel…).
     */
    void delete(CentreId centerId, ReferentialKind kind, UUID id);

    /**
     * Vérifie un fichier et, si {@code dryRun} est faux et que le fichier est entièrement valide, l'importe :
     * création des nouvelles lignes, mise à jour de celles reconnues par leur clé (tout ou rien).
     */
    ImportReport importEntries(CentreId centerId, ReferentialKind kind, ImportTable table, boolean dryRun);
}

