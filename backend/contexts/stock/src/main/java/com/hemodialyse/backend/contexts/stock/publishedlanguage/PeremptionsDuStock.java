package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Stock : les lots encore en stock d'un centre qui périment au plus tard à une date (RG-STK-060), lus par le
 * contrôle quotidien des péremptions.
 */
public interface PeremptionsDuStock {

    /**
     * @return les lots en stock (quantité &gt; 0) dont la date de péremption est au plus tard {@code auPlusTard}, les plus proches
     * d'abord
     */
    List<LotProcheDePeremption> lotsPerimantAuPlusTard(UUID centre, LocalDate auPlusTard);

    record LotProcheDePeremption(UUID lotId, String numeroLot, UUID articleId, LocalDate datePeremption,
                                 BigDecimal quantiteRestante) {
    }
}
