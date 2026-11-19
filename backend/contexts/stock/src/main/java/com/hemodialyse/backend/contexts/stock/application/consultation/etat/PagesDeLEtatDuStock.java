package com.hemodialyse.backend.contexts.stock.application.consultation.etat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage.FiltreNumerique;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;

import java.util.Optional;

/**
 * Port de lecture paginée de l'état du stock (articles actifs du centre valorisés au PMP courant, RG-STK-090) : pagination,
 * tri et filtres exécutés par l'adapter (SQL : {@code COUNT} + {@code LIMIT/OFFSET}).
 */
public interface PagesDeLEtatDuStock {

    PageDeResultats<ArticleValorise> page(CentreId centre, Filtres filtres, DemandeDePage demande);

    /** Filtres de colonne : code et libellé « contient » (texte normalisé), quantité, PMP et valeur comparés. */
    record Filtres(Optional<String> code, Optional<String> libelle, Optional<FiltreNumerique> quantite,
                   Optional<FiltreNumerique> pmp, Optional<FiltreNumerique> valeur) {

        public static Filtres aucun() {
            return new Filtres(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }
}
