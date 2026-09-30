package com.hemodialyse.backend.domain.referential.admin.model;

import java.util.Map;

/**
 * Anomalie détectée sur une valeur (formulaire ou ligne d'import).
 *
 * @param row     numéro de ligne du fichier (0 = anomalie globale ou saisie de formulaire)
 * @param field   clé du champ concerné ({@code null} si globale)
 * @param code    code stable, traduit côté frontend (REQUIRED, TOO_LONG, REFERENCE_NOT_FOUND…)
 * @param message message français prêt à afficher
 * @param params  paramètres du message (valeur, longueur max, valeurs autorisées…)
 */
public record ValidationIssue(int row, String field, String code, String message, Map<String, String> params) {

    public ValidationIssue {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public ValidationIssue atRow(int lineNumber) {
        return new ValidationIssue(lineNumber, field, code, message, params);
    }
}

