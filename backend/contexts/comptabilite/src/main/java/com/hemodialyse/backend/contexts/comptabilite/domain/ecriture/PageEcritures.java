package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import java.util.List;

/**
 * Page d'écritures lue pour un centre et une période.
 */
public record PageEcritures(List<EcritureComptable> items, long total, int page, int size) {
    public PageEcritures {
        items = List.copyOf(items);
    }
}
