package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du Stock : le catalogue d'articles d'un centre (Open Host Service, ADR 0006, slice S1). Seul point d'entrée
 * prévu pour la Séance (consommables, raccourcis) et le Dossier médical (articles de traitement de l'anémie, conversion de dose)
 * ; les consommateurs legacy y passent au fil des slices S5 à S8.
 */
public interface CatalogueDArticles {

    /**
     * L'article du centre ; vide s'il est inconnu ou d'un autre centre.
     */
    Optional<ArticleDuCatalogue> article(UUID centre, UUID article);

    /**
     * Tous les articles du centre, actifs et inactifs, triés par code.
     */
    List<ArticleDuCatalogue> articles(UUID centre);

    /**
     * Les articles du centre d'un type de traitement de l'anémie ({@code EPO}, {@code FER_INJECTABLE}), triés par code.
     */
    List<ArticleDuCatalogue> articlesDeTraitement(UUID centre, String typeTraitementAnemie);

    /**
     * Quantité à sortir pour une dose prescrite (RG-STK-006) : dose ÷ dosage, 4 décimales ; sans dosage, 1:1 si la dose est déjà
     * dans l'unité de stock. Refus métier ({@code ARTICLE_UNITE_DOSE_INCOMPATIBLE}, {@code ARTICLE_DOSAGE_NON_DEFINI},
     * {@code DOSE_ADMINISTREE_INVALIDE}, {@code ARTICLE_INTROUVABLE}) levé en {@link RefusDuCatalogue}.
     */
    BigDecimal quantitePourDose(UUID centre, UUID article, BigDecimal dose, String uniteDose);

    /**
     * Refus du catalogue, avec le code stable du Stock.
     */
    class RefusDuCatalogue extends RuntimeException {
        private final String code;

        public RefusDuCatalogue(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}

