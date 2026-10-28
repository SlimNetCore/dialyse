package com.hemodialyse.backend.contexts.stock.application.referentiel;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.PageDArticles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Pagination;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage du catalogue d'articles (RG-STK-001 à 008) : référencer, modifier la fiche, changer l'activité, consulter,
 * rechercher, lister. Le code d'un article est unique par centre, sans tenir compte de la casse (contrôle applicatif, comme le
 * legacy).
 */
public final class ReferentielDesArticles {

    private final Articles articles;
    private final Supplier<UUID> identifiants;
    private final Clock horloge;

    public ReferentielDesArticles(Articles articles, Supplier<UUID> identifiants, Clock horloge) {
        this.articles = articles;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Article referencer(CentreId centre, FicheArticle fiche) {
        exigerCodeLibre(centre, fiche.code(), null);
        Article article = Article.referencer(identifiants.get(), centre, fiche, OffsetDateTime.now(horloge));
        articles.enregistrer(article);
        return article;
    }

    public Article modifierFiche(CentreId centre, UUID id, FicheArticle fiche) {
        Article article = consulter(centre, id);
        exigerCodeLibre(centre, fiche.code(), id);
        article.modifierFiche(fiche);
        articles.enregistrer(article);
        return article;
    }

    public Article changerActivite(CentreId centre, UUID id, boolean actif) {
        Article article = consulter(centre, id);
        article.changerActivite(actif);
        articles.enregistrer(article);
        return article;
    }

    /**
     * @throws ArticleIntrouvable article inconnu ou d'un autre centre (422, KB-STK-07)
     */
    public Article consulter(CentreId centre, UUID id) {
        return articles.parId(centre, id).orElseThrow(ArticleIntrouvable::new);
    }

    public PageDArticles rechercher(CentreId centre, String texte, Boolean actif, int page, int taille) {
        return articles.rechercher(centre, texte, actif, Pagination.demandee(page, taille));
    }

    public List<Article> lister(CentreId centre) {
        return articles.tous(centre);
    }

    public List<Article> listerParTypeDeTraitement(CentreId centre, TypeTraitementAnemie type) {
        return articles.parTypeDeTraitement(centre, type);
    }

    private void exigerCodeLibre(CentreId centre, String code, UUID articleCourant) {
        boolean pris = articles.parCode(centre, code).filter(autre -> !autre.id().equals(articleCourant)).isPresent();
        if (pris) {
            throw new RefusDuReferentiel("ARTICLE_CODE_EXISTANT", "Ce code article existe déjà dans le centre");
        }
    }
}


