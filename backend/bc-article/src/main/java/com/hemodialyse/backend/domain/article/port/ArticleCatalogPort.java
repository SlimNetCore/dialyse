package com.hemodialyse.backend.domain.article.port;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;

/**
 * Port de consultation du catalogue des articles (recherche, unicité du code) — séparé de
 * {@link ArticleRepositoryPort} (ségrégation des interfaces) : seul l'écran « fiche article » en a besoin.
 */
public interface ArticleCatalogPort {

    Optional<Article> findByCode(CenterId centerId, String code);

    /**
     * Page d'articles du centre, triés par code ; {@code query} filtre sur code, libellé, DCI et code-barres
     * (insensible à la casse), {@code active} sur l'état (null = tous).
     */
    PagedResult<Article> findPaged(CenterId centerId, String query, Boolean active, int page, int size);
}
