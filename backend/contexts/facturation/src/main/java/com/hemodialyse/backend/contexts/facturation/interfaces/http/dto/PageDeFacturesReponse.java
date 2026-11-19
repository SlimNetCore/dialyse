package com.hemodialyse.backend.contexts.facturation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.facturation.application.consultation.PageDeFactures;
import com.hemodialyse.backend.contexts.facturation.application.consultation.TotauxDesFactures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Une page de la liste des factures, la période effective et les totaux de toutes les factures filtrées (RG-FAC-046). */
public record PageDeFacturesReponse(List<FactureListeeReponse> items, long total, int page, int size, LocalDate du, LocalDate au,
                                    Totaux totaux) {

    public record Totaux(long nombreDeFactures, BigDecimal montantHt, BigDecimal montantTva, BigDecimal montantTtc,
                         BigDecimal montantRegle, BigDecimal reste) {
    }

    public static PageDeFacturesReponse depuis(PageDeFactures page) {
        TotauxDesFactures t = page.totaux();
        return new PageDeFacturesReponse(page.items().stream().map(FactureListeeReponse::depuis).toList(), page.total(), page.page(),
                page.taille(), page.du(), page.au(),
                new Totaux(t.nombreDeFactures(), t.montantHt(), t.montantTva(), t.montantTtc(), t.montantRegle(), t.reste()));
    }
}
