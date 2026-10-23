package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import java.util.List;

/**
 * Une page d'articles et le total des articles qui répondent à la recherche.
 */
public record PageDArticles(List<Article> articles, long total, Pagination pagination) {
}

