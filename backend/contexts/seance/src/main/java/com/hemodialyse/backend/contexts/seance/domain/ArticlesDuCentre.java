package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : le catalogue d'articles du centre (propriété du Stock). Inconnu ou d'un autre centre : vide. */
public interface ArticlesDuCentre {
    Optional<ArticleDuCentre> trouver(CentreId centre, ArticleId article);
}
