package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.domain.ArticleDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.ArticlesDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;

import java.util.Optional;

/** ACL vers le catalogue publié du Stock ({@link CatalogueDArticles}) : l'article du centre, réduit à ce que la Séance en sait. */
public class ArticlesDuCentreDuStock implements ArticlesDuCentre {

    private final CatalogueDArticles catalogue;

    public ArticlesDuCentreDuStock(CatalogueDArticles catalogue) {
        this.catalogue = catalogue;
    }

    @Override
    public Optional<ArticleDuCentre> trouver(CentreId centre, ArticleId article) {
        return catalogue.article(centre.valeur(), article.valeur()).map(a -> new ArticleDuCentre(new ArticleId(a.id()), a.code(), a.actif()));
    }
}
