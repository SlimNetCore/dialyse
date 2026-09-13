package com.hemodialyse.backend.domain.medical.observation.valueobject;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;

/**
 * Value Object — valeur numérique mesurée, toujours accompagnée de son unité. Une valeur sans
 * unité n'est pas exploitable cliniquement (AGENTS.md §14 : pas de primitive obsession).
 */
public record ValeurMesuree(BigDecimal valeur, String unite) {

    public ValeurMesuree {
        if (valeur == null) {
            throw new BusinessException("VALEUR_MESUREE_REQUISE", "La valeur mesurée est obligatoire");
        }
        if (unite == null || unite.isBlank()) {
            throw new BusinessException("VALEUR_MESUREE_UNITE_REQUISE", "L'unité est obligatoire pour une valeur numérique");
        }
    }
}
