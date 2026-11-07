package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers le langage publié du Stock ({@link CatalogueDArticles}, Open Host Service) : seul le code et le libellé des articles sont
 * repris ; les refus du catalogue gardent leur code stable (422), comme avant la reprise.
 */
public class CatalogueDesArticlesDuStock implements CatalogueDesArticles {

    private final CatalogueDArticles stock;

    public CatalogueDesArticlesDuStock(CatalogueDArticles stock) {
        this.stock = stock;
    }

    @Override
    public Optional<ArticleDeTraitement> article(CentreId centre, UUID article) {
        return stock.article(centre.valeur(), article).map(a -> new ArticleDeTraitement(a.id(), a.code(), a.libelle()));
    }

    @Override
    public BigDecimal quantitePourLaDose(CentreId centre, UUID article, BigDecimal dose, String unite) {
        try {
            return stock.quantitePourDose(centre.valeur(), article, dose, unite);
        } catch (CatalogueDArticles.RefusDuCatalogue refus) {
            throw new RegleDeGestionViolee(refus.code(), refus.getMessage());
        }
    }

    @Override
    public void verifierLaConversionDeLaDose(CentreId centre, UUID article, int dose, String unite) {
        try {
            stock.quantitePourDose(centre.valeur(), article, BigDecimal.valueOf(dose), unite);
        } catch (CatalogueDArticles.RefusDuCatalogue refus) {
            throw new RegleDeGestionViolee(refus.code(), refus.getMessage());
        }
    }
}
