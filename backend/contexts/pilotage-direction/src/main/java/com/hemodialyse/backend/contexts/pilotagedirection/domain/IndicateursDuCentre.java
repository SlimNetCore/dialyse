package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

/**
 * Les indicateurs d'un centre de la société, ou ses totaux (identité et nom absents, centre actif) : cliniques (Dossier médical) et stock.
 */
public record IndicateursDuCentre(UUID centreId, String nom, boolean actif, IndicateursCliniquesDuCentre clinique, StockDuCentre stock) {

    static IndicateursDuCentre totaux(IndicateursCliniquesDuCentre consolides, StockDuCentre stock) {
        return new IndicateursDuCentre(null, null, true, consolides, stock);
    }
}
