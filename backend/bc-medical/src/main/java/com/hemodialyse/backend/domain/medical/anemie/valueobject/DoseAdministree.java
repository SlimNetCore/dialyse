package com.hemodialyse.backend.domain.medical.anemie.valueobject;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;

/**
 * Value Object — dose réellement administrée, toujours accompagnée de son unité (AGENTS.md §14 :
 * pas de primitive obsession). Distincte de la dose prescrite : c'est le suivi de ce qui a été
 * fait, pas de ce qui était prévu.
 */
public record DoseAdministree(BigDecimal valeur, String unite) {

    public DoseAdministree {
        if (valeur == null || valeur.signum() <= 0) {
            throw new BusinessException("DOSE_ADMINISTREE_INVALIDE", "La dose administrée doit être strictement positive");
        }
        if (unite == null || unite.isBlank()) {
            throw new BusinessException("DOSE_ADMINISTREE_UNITE_REQUISE", "L'unité de la dose est obligatoire");
        }
    }
}
