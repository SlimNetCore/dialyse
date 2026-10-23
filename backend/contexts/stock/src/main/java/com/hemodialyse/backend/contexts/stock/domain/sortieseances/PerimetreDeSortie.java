package com.hemodialyse.backend.contexts.stock.domain.sortieseances;

/**
 * Périmètre d'une sortie pour séances : salle et/ou demi-journée (tous deux facultatifs ; sans l'un ni l'autre, la pièce « journée »
 * du centre). Deux pièces du même jour et du même périmètre se suivent : la précédente doit être rapprochée.
 */
public record PerimetreDeSortie(String salle, DemiJournee demiJournee) {
    public PerimetreDeSortie {
        salle = salle == null || salle.isBlank() ? null : salle.trim();
    }

    public static PerimetreDeSortie journee() {
        return new PerimetreDeSortie(null, null);
    }
}