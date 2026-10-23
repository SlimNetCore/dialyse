package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fiche article : invariants de saisie (RG-STK-001, 006, 008), ordre et codes du legacy, nettoyage.
 */
class FicheArticleTest {

    private static final BigDecimal UN = BigDecimal.ONE;

    private static FicheArticle fiche(String code, String libelle, String unite, BigDecimal coefficient, BigDecimal dosage,
                                      String uniteDosage, BigDecimal prix, BigDecimal seuil, BigDecimal stockMax,
                                      String compteStock, String compteCharge) {
        return FicheArticle.saisie(code, libelle, null, null, null, null, unite, null, coefficient, dosage, uniteDosage,
                null, null, prix, seuil, stockMax, false, false, null, false, false, null, compteStock, compteCharge);
    }

    private static String refus(Runnable saisie) {
        return assertThrows(RefusDuReferentiel.class, saisie::run).code();
    }

    @Test
    void code_libelle_et_unite_sont_obligatoires_dans_cet_ordre() {
        assertEquals("ARTICLE_CODE_REQUIS", refus(() -> fiche(" ", null, null, null, null, null, null, null, null, null, null)));
        assertEquals("ARTICLE_LIBELLE_REQUIS", refus(() -> fiche("C", "", null, null, null, null, null, null, null, null, null)));
        assertEquals("ARTICLE_UNITE_REQUISE", refus(() -> fiche("C", "L", " ", null, null, null, null, null, null, null, null)));
    }

    @Test
    void le_dosage_et_son_unite_vont_ensemble_et_le_dosage_est_strictement_positif() {
        assertEquals("ARTICLE_DOSAGE_INCOMPLET", refus(() -> fiche("C", "L", "u", null, UN, null, null, null, null, null, null)));
        assertEquals("ARTICLE_DOSAGE_INCOMPLET", refus(() -> fiche("C", "L", "u", null, null, "UI", null, null, null, null, null)));
        assertEquals("ARTICLE_DOSAGE_INVALIDE",
                refus(() -> fiche("C", "L", "u", null, BigDecimal.ZERO, "UI", null, null, null, null, null)));
    }

    @Test
    void coefficient_strictement_positif_et_valeurs_non_negatives() {
        assertEquals("ARTICLE_COEFFICIENT_ACHAT_INVALIDE",
                refus(() -> fiche("C", "L", "u", BigDecimal.ZERO, null, null, null, null, null, null, null)));
        BigDecimal moinsUn = BigDecimal.ONE.negate();
        assertEquals("ARTICLE_VALEUR_NEGATIVE", refus(() -> fiche("C", "L", "u", null, null, null, moinsUn, null, null, null, null)));
        assertEquals("ARTICLE_VALEUR_NEGATIVE", refus(() -> fiche("C", "L", "u", null, null, null, null, moinsUn, null, null, null)));
        assertEquals("ARTICLE_VALEUR_NEGATIVE", refus(() -> fiche("C", "L", "u", null, null, null, null, null, moinsUn, null, null)));
    }

    @Test
    void le_stock_maximum_ne_peut_etre_inferieur_au_seuil_absent_valant_zero() {
        assertEquals("ARTICLE_STOCK_MAX_INFERIEUR_SEUIL",
                refus(() -> fiche("C", "L", "u", null, null, null, null, BigDecimal.TEN, UN, null, null)));
        FicheArticle egal = fiche("C", "L", "u", null, null, null, null, null, BigDecimal.ZERO, null, null);
        assertEquals(BigDecimal.ZERO, egal.seuilAlerte());
    }

    @Test
    void un_compte_comporte_1_a_20_lettres_ou_chiffres() {
        assertEquals("ARTICLE_COMPTE_INVALIDE", refus(() -> fiche("C", "L", "u", null, null, null, null, null, null, "311-00", null)));
        assertEquals("ARTICLE_COMPTE_INVALIDE",
                refus(() -> fiche("C", "L", "u", null, null, null, null, null, null, null, "6".repeat(21))));
        assertEquals("60110", fiche("C", "L", "u", null, null, null, null, null, null, null, " 60110 ").compteCharge());
    }

    @Test
    void la_fiche_est_nettoyee() {
        FicheArticle f = FicheArticle.saisie(" EPO ", " Epoetine ", "  ", null, " 340 ", null, " seringue ", " ", null,
                null, null, null, null, null, null, null, true, false, null, false, true, null, " ", null);
        assertEquals("EPO", f.code());
        assertEquals("Epoetine", f.libelle());
        assertEquals("seringue", f.unite());
        assertEquals("340", f.codeBarres());
        assertNull(f.dci());
        assertNull(f.uniteAchat());
        assertNull(f.compteStock());
    }
}

