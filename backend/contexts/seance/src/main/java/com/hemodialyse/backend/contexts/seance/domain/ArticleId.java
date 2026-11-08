package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

/** Identité d'un article du catalogue du centre (référence du Stock, jamais l'article lui-même). */
public record ArticleId(UUID valeur) {
    public ArticleId {
        if (valeur == null) throw new SaisieInvalide("Article requis");
    }
}
