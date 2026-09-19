package com.hemodialyse.backend.domain.medical.kdigo.valueobject;

import java.math.BigDecimal;

/**
 * Résultat de l'évaluation d'un niveau de risque (par opposition à une cible numérique bornée,
 * cf. {@link EvaluationCible}) face à un référentiel KDIGO — utilisé pour le risque
 * immunologique (PRA) du bilan pré-greffe.
 */
public record EvaluationRisqueKdigo(
        String code,
        BigDecimal valeur,
        String unite,
        NiveauRisqueKdigo niveau,
        String referenceKdigo
) {
}
