package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter entrant fourni aux autres contextes : le langage publié {@link CatalogueDArticles} (OHS, ADR 0006), servi par les cas
 * d'usage du référentiel. Lecture seule, sans garde de session (l'appelant a déjà résolu son centre).
 */
public class CatalogueDArticlesDuReferentiel implements CatalogueDArticles {

    private final ReferentielDesArticles referentiel;

    public CatalogueDArticlesDuReferentiel(ReferentielDesArticles referentiel) {
        this.referentiel = referentiel;
    }

    private static ArticleDuCatalogue publie(Article a) {
        FicheArticle f = a.fiche();
        return new ArticleDuCatalogue(a.id(), a.centre().valeur(), f.code(), f.libelle(), f.unite(), a.actif(),
                f.typeTraitementAnemie() == null ? null : f.typeTraitementAnemie().name(), f.dosageParUnite(), f.uniteDosage(),
                f.gereParLot(), f.compteStock(), f.compteCharge());
    }

    @Override
    public Optional<ArticleDuCatalogue> article(UUID centre, UUID article) {
        try {
            return Optional.of(publie(referentiel.consulter(new CentreId(centre), article)));
        } catch (ArticleIntrouvable e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ArticleDuCatalogue> articles(UUID centre) {
        return referentiel.lister(new CentreId(centre)).stream().map(CatalogueDArticlesDuReferentiel::publie).toList();
    }

    @Override
    public List<ArticleDuCatalogue> articlesDeTraitement(UUID centre, String typeTraitementAnemie) {
        TypeTraitementAnemie type;
        try {
            type = TypeTraitementAnemie.valueOf(typeTraitementAnemie);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RefusDuCatalogue("ARTICLE_TYPE_TRAITEMENT_INVALIDE",
                    "Type de traitement de l'anémie inconnu : " + typeTraitementAnemie + " (EPO ou FER_INJECTABLE)");
        }
        return referentiel.listerParTypeDeTraitement(new CentreId(centre), type).stream()
                .map(CatalogueDArticlesDuReferentiel::publie).toList();
    }

    @Override
    public BigDecimal quantitePourDose(UUID centre, UUID article, BigDecimal dose, String uniteDose) {
        try {
            return referentiel.consulter(new CentreId(centre), article).fiche().dosage().quantitePourDose(dose, uniteDose);
        } catch (ArticleIntrouvable e) {
            throw new RefusDuCatalogue("ARTICLE_INTROUVABLE", e.getMessage());
        } catch (RefusDuReferentiel e) {
            throw new RefusDuCatalogue(e.code(), e.getMessage());
        }
    }
}

