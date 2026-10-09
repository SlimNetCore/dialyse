package com.hemodialyse.backend.domain.article.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleTest {

    private static ArticleFiche fiche(BigDecimal dosage, String uniteDosage, BigDecimal seuil, BigDecimal stockMax) {
        return new ArticleFiche("EPO-4000", "Époétine 4000 UI", " Époétine alfa ", "Solution injectable", " ", null,
                "seringue", "boîte", new BigDecimal("6"), dosage, uniteDosage, null, null, new BigDecimal("2500"),
                seuil, stockMax, true, true, ConditionConservation.REFRIGERE, false, true,
                TypeTraitementAnemie.EPO, " 321 ", " ");
    }

    @Test
    void les_comptes_comptables_de_l_article_sont_facultatifs_et_controles() {
        Article article = article(null, null);
        assertEquals("321", article.getCompteStock());
        assertNull(article.getCompteCharge(), "vide = compte du centre");

        var invalide = new ArticleFiche("A", "B", null, null, null, null, "u", null, null, null, null, null, null,
                null, null, null, false, false, null, false, false, null, "32-1", null);
        assertEquals("ARTICLE_COMPTE_INVALIDE",
                assertThrows(BusinessException.class, () -> new Article().appliquerFiche(invalide)).getCode());
    }

    private static Article article(BigDecimal dosage, String uniteDosage) {
        Article article = new Article();
        article.appliquerFiche(fiche(dosage, uniteDosage, new BigDecimal("10"), new BigDecimal("100")));
        return article;
    }

    @Test
    void appliquer_fiche_nettoie_les_textes_et_reporte_toutes_les_donnees() {
        Article article = article(new BigDecimal("4000"), " UI ");

        assertEquals("Époétine alfa", article.getDci());
        assertNull(article.getCodeBarres());
        assertEquals("UI", article.getUniteDosage());
        assertEquals(ConditionConservation.REFRIGERE, article.getConditionConservation());
        assertTrue(article.isPeremptionObligatoire());
        assertTrue(article.isDechetDasri());
        assertFalse(article.isProduitDangereux());
        assertEquals(new BigDecimal("6"), article.getCoefficientAchat());
    }

    @Test
    void le_dosage_et_son_unite_vont_ensemble() {
        var sansUnite = assertThrows(BusinessException.class,
                () -> new Article().appliquerFiche(fiche(new BigDecimal("4000"), null, BigDecimal.ONE, null)));
        var sansDosage = assertThrows(BusinessException.class,
                () -> new Article().appliquerFiche(fiche(null, "UI", BigDecimal.ONE, null)));

        assertEquals("ARTICLE_DOSAGE_INCOMPLET", sansUnite.getCode());
        assertEquals("ARTICLE_DOSAGE_INCOMPLET", sansDosage.getCode());
    }

    @Test
    void refuse_un_dosage_ou_un_coefficient_non_positif_et_un_stock_max_sous_le_seuil() {
        assertEquals("ARTICLE_DOSAGE_INVALIDE", assertThrows(BusinessException.class,
                () -> new Article().appliquerFiche(fiche(BigDecimal.ZERO, "UI", BigDecimal.ONE, null))).getCode());
        assertEquals("ARTICLE_STOCK_MAX_INFERIEUR_SEUIL", assertThrows(BusinessException.class,
                () -> new Article().appliquerFiche(
                        fiche(null, null, new BigDecimal("50"), new BigDecimal("10")))).getCode());
        var coefficient = new ArticleFiche("A", "B", null, null, null, null, "u", null, BigDecimal.ZERO, null, null,
                null, null, null, null, null, false, false, null, false, false, null, null, null);
        assertEquals("ARTICLE_COEFFICIENT_ACHAT_INVALIDE",
                assertThrows(BusinessException.class, () -> new Article().appliquerFiche(coefficient)).getCode());
    }

    @Test
    void exige_code_libelle_et_unite() {
        var sansCode = new ArticleFiche(" ", "B", null, null, null, null, "u", null, null, null, null, null, null,
                null, null, null, false, false, null, false, false, null, null, null);
        var sansUnite = new ArticleFiche("A", "B", null, null, null, null, "", null, null, null, null, null, null,
                null, null, null, false, false, null, false, false, null, null, null);

        assertEquals("ARTICLE_CODE_REQUIS",
                assertThrows(BusinessException.class, () -> new Article().appliquerFiche(sansCode)).getCode());
        assertEquals("ARTICLE_UNITE_REQUISE",
                assertThrows(BusinessException.class, () -> new Article().appliquerFiche(sansUnite)).getCode());
    }

    @Test
    void convertit_une_dose_en_quantite_de_stock_selon_le_dosage_par_unite() {
        Article epo = article(new BigDecimal("4000"), "UI");

        assertEquals(new BigDecimal("2.0000"), epo.quantiteStockPourDose(new BigDecimal("8000"), "ui"));
        assertEquals(new BigDecimal("0.7500"), epo.quantiteStockPourDose(new BigDecimal("3000"), "UI"));
    }

    @Test
    void refuse_une_dose_dans_une_autre_unite_que_celle_de_l_article() {
        Article epo = article(new BigDecimal("4000"), "UI");

        var erreur = assertThrows(BusinessException.class, () -> epo.quantiteStockPourDose(new BigDecimal("100"), "mg"));

        assertEquals("ARTICLE_UNITE_DOSE_INCOMPATIBLE", erreur.getCode());
    }

    @Test
    void sans_dosage_seule_l_unite_de_stock_est_acceptee() {
        Article article = new Article();
        article.appliquerFiche(fiche(null, null, BigDecimal.ONE, null));

        assertEquals(new BigDecimal("3"), article.quantiteStockPourDose(new BigDecimal("3"), "seringue"));
        assertEquals("ARTICLE_DOSAGE_NON_DEFINI", assertThrows(BusinessException.class,
                () -> article.quantiteStockPourDose(new BigDecimal("4000"), "UI")).getCode());
    }

    @Test
    void refuse_une_dose_non_positive() {
        Article epo = article(new BigDecimal("4000"), "UI");

        assertThrows(BusinessException.class, () -> epo.quantiteStockPourDose(BigDecimal.ZERO, "UI"));
        assertThrows(BusinessException.class, () -> epo.quantiteStockPourDose(null, "UI"));
    }
}
