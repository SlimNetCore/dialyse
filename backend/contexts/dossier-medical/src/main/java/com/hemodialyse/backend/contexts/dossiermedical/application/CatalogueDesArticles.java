package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : le catalogue d'articles du centre (tenu par le Stock, hors contexte), vu sous l'angle du traitement de l'anémie. */
public interface CatalogueDesArticles {

    /** Code et libellé d'un article du traitement. */
    record ArticleDeTraitement(UUID id, String code, String libelle) {
    }

    /** L'article du centre ; vide s'il est inconnu ou d'un autre centre. */
    Optional<ArticleDeTraitement> article(CentreId centre, UUID article);

    /**
     * Vérifie qu'une dose prescrite (dans l'unité {@code UI} ou {@code mg}) se convertit dans l'unité de stock de l'article (RG-STK-006).
     * Refus : {@link com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee} avec le code stable du Stock
     * ({@code ARTICLE_DOSAGE_NON_DEFINI}, {@code ARTICLE_UNITE_DOSE_INCOMPATIBLE}...).
     */
    void verifierLaConversionDeLaDose(CentreId centre, UUID article, int dose, String unite);

    /** Quantité d'article à sortir pour une dose (dose / dosage, RG-STK-006) ; mêmes refus que ci-dessus, article inconnu compris. */
    java.math.BigDecimal quantitePourLaDose(CentreId centre, UUID article, java.math.BigDecimal dose, String unite);
}
