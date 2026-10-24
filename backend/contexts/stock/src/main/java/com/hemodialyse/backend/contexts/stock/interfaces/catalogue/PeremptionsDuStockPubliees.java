package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.PeremptionsDuStock;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié des péremptions, au-dessus des lectures du Stock (même requête que l'alerte de péremption du tableau de bord).
 */
public class PeremptionsDuStockPubliees implements PeremptionsDuStock {
    private final LecturesDuStock lectures;

    public PeremptionsDuStockPubliees(LecturesDuStock lectures) {
        this.lectures = lectures;
    }

    @Override
    public List<LotProcheDePeremption> lotsPerimantAuPlusTard(UUID centre, LocalDate auPlusTard) {
        return lectures.peremptions(new CentreId(centre), auPlusTard).stream()
                .map(a -> new LotProcheDePeremption(a.lotId(), a.numeroLot(), a.articleId(), a.datePeremption(), a.quantite()))
                .toList();
    }
}
