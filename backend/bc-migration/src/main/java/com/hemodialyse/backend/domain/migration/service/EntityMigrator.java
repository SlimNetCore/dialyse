package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.IdMappingPort;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Map;

/**
 * Reprise d'une donnée (assurés, patients, affectations…). Ajouter une donnée reprise = ajouter une
 * implémentation, sans modifier le service de reprise (principe ouvert / fermé).
 */
public interface EntityMigrator {

    static ValidationIssue issue(int line, String field, String code, String message, Map<String, String> params) {
        return new ValidationIssue(line, field, code, message, params);
    }

    MigrationEntity entity();

    /**
     * Contrôle les lignes (références, doublons, cohérence) et, si {@code write} est vrai et qu'aucune anomalie
     * n'est trouvée, les écrit. N'écrit jamais rien en présence d'une anomalie.
     */
    Outcome migrate(Context context, List<Row> rows, boolean write);

    /**
     * Contexte d'exécution : centre, lot et correspondances d'identifiants.
     */
    record Context(CenterId centerId, MigrationBatch batch, IdMappingPort ids) {
    }

    /**
     * Ligne dont les valeurs sont déjà normalisées par {@link LegacyValueParser}.
     */
    record Row(int line, Map<String, String> values) {
        public String get(String key) {
            return values.get(key);
        }
    }

    /**
     * Résultat : créations / mises à jour (prévues ou effectuées), anomalies et points d'attention.
     */
    record Outcome(int created, int updated, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        public Outcome {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }
}

