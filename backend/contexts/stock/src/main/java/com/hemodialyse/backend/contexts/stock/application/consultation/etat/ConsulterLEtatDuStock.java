package com.hemodialyse.backend.contexts.stock.application.consultation.etat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;

import java.util.Set;

/**
 * État du stock paginé (RG-STK-090) : articles actifs du centre avec quantité, PMP courant et valeur ({@code quantité × PMP}).
 * Tri sur {@code code}, {@code libelle}, {@code quantite}, {@code pmp}, {@code valeur} (défaut : code croissant) ; filtres texte
 * {@code code}, {@code libelle} et numériques {@code quantite}, {@code pmp}, {@code valeur}.
 */
public final class ConsulterLEtatDuStock {

    public static final Set<String> CHAMPS_TRIABLES = Set.of("code", "libelle", "quantite", "pmp", "valeur");

    private final PagesDeLEtatDuStock pages;

    public ConsulterLEtatDuStock(PagesDeLEtatDuStock pages) {
        this.pages = pages;
    }

    /**
     * @throws com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide page, taille, tri ou filtre refusés
     */
    public PageDeResultats<ArticleValorise> page(CentreId centre, Integer page, Integer taille, String tri, String code,
                                                 String libelle, String quantite, String pmp, String valeur) {
        DemandeDePage demande = DemandeDePage.lire(page, taille, tri, CHAMPS_TRIABLES, "code", false);
        PagesDeLEtatDuStock.Filtres filtres = new PagesDeLEtatDuStock.Filtres(FiltresDePage.texte(code), FiltresDePage.texte(libelle),
                FiltresDePage.numerique("quantite", quantite), FiltresDePage.numerique("pmp", pmp),
                FiltresDePage.numerique("valeur", valeur));
        return pages.page(centre, filtres, demande);
    }
}
