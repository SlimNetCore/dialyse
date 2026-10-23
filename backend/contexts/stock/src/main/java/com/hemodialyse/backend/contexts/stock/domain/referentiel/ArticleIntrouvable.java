package com.hemodialyse.backend.contexts.stock.domain.referentiel;

/**
 * Article inconnu ou d'un autre centre (indistinguables) : 422 {@code ARTICLE_INTROUVABLE}, comme le legacy (KB-STK-07
 * reproduit, décision utilisateur du 2026-10-23).
 */
public class ArticleIntrouvable extends RuntimeException {
    public ArticleIntrouvable() {
        super("Article introuvable");
    }
}


