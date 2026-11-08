package com.hemodialyse.backend.contexts.seance.domain;

/** Ce que la Séance sait d'un article du catalogue du centre : son code et s'il est actif (le reste appartient au Stock). */
public record ArticleDuCentre(ArticleId id, String code, boolean actif) {
}
