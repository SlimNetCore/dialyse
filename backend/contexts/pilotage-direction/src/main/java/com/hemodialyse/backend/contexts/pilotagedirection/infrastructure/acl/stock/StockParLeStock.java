package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.StockDuCentre;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ACL : le stock des centres lu dans {@code stock.v1} ({@link IndicateursDuStock}, S9) à la date demandée. Traduction pure. */
public final class StockParLeStock implements StockDesCentres {

    private final IndicateursDuStock stock;

    public StockParLeStock(IndicateursDuStock stock) {
        this.stock = stock;
    }

    @Override
    public Map<UUID, StockDuCentre> aLaDate(List<UUID> centres, LocalDate date) {
        Map<UUID, StockDuCentre> traduits = new HashMap<>();
        stock.pourCentres(centres, date).forEach((centre, i) -> traduits.put(centre,
                new StockDuCentre(i.articlesActifs(), i.articlesSousSeuil(), i.lotsPerimes(), i.lotsProches(), i.valeurStock())));
        return traduits;
    }
}
