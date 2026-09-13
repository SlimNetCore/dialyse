package com.hemodialyse.backend.domain.medical.kdigo.valueobject;

import java.math.BigDecimal;

/**
 * Résultat de l'évaluation d'une valeur clinique face à une {@link RegleCibleKdigo}.
 */
public record EvaluationCible(
        String code,
        BigDecimal valeur,
        String unite,
        StatutCible statut,
        BigDecimal borneMin,
        BigDecimal borneMax,
        String referenceKdigo
) {
}
