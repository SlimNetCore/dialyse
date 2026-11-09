package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ce que la direction sait du stock d'un centre, évalué à la FIN de la période (KB-DIR-07 : date de fin, pas la date du jour) : articles
 * actifs, articles sous le seuil d'alerte, lots périmés, lots proches de la péremption, et valeur du stock (quantité x prix moyen pondéré).
 * Des quantités, jamais de donnée nominative : aucun masquage.
 * <p>
 * Les totaux de la société additionnent les valeurs NON arrondies des centres, puis arrondissent une fois : {@link #arrondi()} ne s'applique
 * qu'à ce qu'on publie (0,003 + 0,003 donne 0,01, non 0,00 + 0,00).
 */
public record StockDuCentre(long articlesActifs, long articlesSousSeuil, long lotsPerimes, long lotsProches, BigDecimal valeurStock) {

    /** Un centre sans article, ou inconnu du Stock : tout à zéro. */
    public static final StockDuCentre VIDE = new StockDuCentre(0, 0, 0, 0, BigDecimal.ZERO);

    public StockDuCentre plus(StockDuCentre autre) {
        return new StockDuCentre(articlesActifs + autre.articlesActifs, articlesSousSeuil + autre.articlesSousSeuil,
                lotsPerimes + autre.lotsPerimes, lotsProches + autre.lotsProches, valeurStock.add(autre.valeurStock));
    }

    /** La valeur arrondie au centime (demi supérieur), telle qu'elle est publiée. */
    public StockDuCentre arrondi() {
        return new StockDuCentre(articlesActifs, articlesSousSeuil, lotsPerimes, lotsProches, valeurStock.setScale(2, RoundingMode.HALF_UP));
    }
}
