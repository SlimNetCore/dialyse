package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Emplacement;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.PageDArticles;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Corps et réponses JSON du référentiel, identiques à ceux du legacy (mêmes noms de champs, valeurs telles qu'enregistrées).
 */
public final class ReferentielJson {

    private ReferentielJson() {
    }

    public record FournisseurRequete(@NotNull UUID centerId, String code, @NotBlank String raisonSociale,
                                     String contact,
                                     String telephone, String email) {
    }

    public record EmplacementRequete(@NotNull UUID centerId, String code, @NotBlank String libelle) {
    }

    public record ArticleReponse(UUID id, UUID centerId, String code, String libelle, String unite,
                                 BigDecimal stockQuantity,
                                 BigDecimal seuilAlerte, BigDecimal pmpCourant, boolean gereParLot, boolean active,
                                 String typeTraitementAnemie, OffsetDateTime createdAt, String dci,
                                 String formeGalenique,
                                 String codeBarres, String referenceFabricant, String uniteAchat,
                                 BigDecimal coefficientAchat,
                                 BigDecimal dosageParUnite, String uniteDosage, UUID fournisseurId, UUID tvaTypeId,
                                 BigDecimal prixAchat, BigDecimal stockMax, boolean peremptionObligatoire,
                                 String conditionConservation, boolean produitDangereux, boolean dechetDasri,
                                 String compteStock,
                                 String compteCharge) {

        static ArticleReponse depuis(Article a) {
            FicheArticle f = a.fiche();
            return new ArticleReponse(a.id(), a.centre().valeur(), f.code(), f.libelle(), f.unite(), a.stock(), f.seuilAlerte(),
                    a.pmp(), f.gereParLot(), a.actif(), f.typeTraitementAnemie() == null ? null : f.typeTraitementAnemie().name(),
                    a.creeLe(), f.dci(), f.formeGalenique(), f.codeBarres(), f.referenceFabricant(), f.uniteAchat(),
                    f.coefficientAchat(), f.dosageParUnite(), f.uniteDosage(), f.fournisseurId(), f.tvaTypeId(), f.prixAchat(),
                    f.stockMax(), f.peremptionObligatoire(),
                    f.conditionConservation() == null ? null : f.conditionConservation().name(), f.produitDangereux(),
                    f.dechetDasri(), f.compteStock(), f.compteCharge());
        }

        static List<ArticleReponse> depuis(List<Article> articles) {
            return articles.stream().map(ArticleReponse::depuis).toList();
        }
    }

    public record PageReponse(List<ArticleReponse> items, int page, int size, long total) {
        static PageReponse depuis(PageDArticles page) {
            return new PageReponse(ArticleReponse.depuis(page.articles()), page.pagination().page(), page.pagination().taille(),
                    page.total());
        }
    }

    public record FournisseurReponse(UUID id, UUID centerId, String code, String raisonSociale, String contact,
                                     String telephone,
                                     String email, boolean actif) {
        static FournisseurReponse depuis(Fournisseur f) {
            return new FournisseurReponse(f.id(), f.centre().valeur(), f.code(), f.raisonSociale(), f.contact(), f.telephone(),
                    f.email(), f.actif());
        }
    }

    public record EmplacementReponse(UUID id, UUID centerId, String code, String libelle, boolean actif) {
        static EmplacementReponse depuis(Emplacement e) {
            return new EmplacementReponse(e.id(), e.centre().valeur(), e.code(), e.libelle(), e.actif());
        }
    }
}

