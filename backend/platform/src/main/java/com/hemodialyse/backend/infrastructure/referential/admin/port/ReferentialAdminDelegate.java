package com.hemodialyse.backend.infrastructure.referential.admin.port;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant de l'administration générique : un référentiel dont un contexte est devenu propriétaire (RF1 : les transporteurs,
 * patient-couverture) n'est plus lu ni écrit par le dépôt générique ; la saisie, la liste, la suppression et les lignes importées
 * lui sont déléguées. La lecture du fichier (en-têtes, bornes, compte rendu) reste à l'administration générique.
 * Les refus sont rendus dans les types de l'administration ({@code ReferentialValidationException}, {@code BusinessException}).
 */
public interface ReferentialAdminDelegate {

    /** Ce référentiel est-il géré par le délégué ? */
    boolean prend(ReferentialKind kind);

    PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size);

    ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values);

    ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values);

    void delete(CenterId centerId, ReferentialKind kind, UUID id);

    /** Contrôle (et, sauf vérification seule, écrit tout ou rien) les lignes lues du fichier. */
    Outcome importRows(CenterId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun);

    /** Une ligne de données du fichier : son numéro et ses valeurs par clé de champ. */
    record Row(int lineNumber, Map<String, String> values) {
    }

    /** Résultat : lignes à créer, à mettre à jour, anomalies (toutes) et écriture effective. */
    record Outcome(int toCreate, int toUpdate, List<ValidationIssue> errors, boolean applied) {
    }
}
