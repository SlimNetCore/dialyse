package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion d'une dose prescrite (UI, mg…) en quantité à sortir, exprimée dans l'unité de stock (RG-STK-006) :
 * {@code dose ÷ dosage}, 4 décimales, arrondi demi-supérieur. Sans dosage, seule une dose déjà exprimée dans l'unité de stock
 * est acceptée (1:1) ; toute autre unité est refusée : le stock n'est jamais décrémenté « au hasard ». Mêmes codes que le
 * legacy ({@code Article.quantiteStockPourDose}).
 *
 * @param dosageParUnite quantité de produit actif d'une unité de stock (absente si non renseignée)
 * @param uniteDosage    unité du dosage (UI, mg), absente si le dosage l'est
 * @param uniteDeStock   unité de stock de l'article
 * @param code           code de l'article (messages)
 */
public record Dosage(BigDecimal dosageParUnite, String uniteDosage, String uniteDeStock, String code) {

    private static boolean memeUnite(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    public BigDecimal quantitePourDose(BigDecimal dose, String uniteDose) {
        if (dose == null || dose.signum() <= 0) {
            throw new RefusDuReferentiel("DOSE_ADMINISTREE_INVALIDE", "La dose administrée doit être strictement positive");
        }
        if (dosageParUnite != null) {
            if (!memeUnite(uniteDose, uniteDosage)) {
                throw new RefusDuReferentiel("ARTICLE_UNITE_DOSE_INCOMPATIBLE",
                        "L'article " + code + " se dose en " + uniteDosage + ", pas en " + uniteDose);
            }
            return dose.divide(dosageParUnite, 4, RoundingMode.HALF_UP);
        }
        if (memeUnite(uniteDose, uniteDeStock)) {
            return dose;
        }
        throw new RefusDuReferentiel("ARTICLE_DOSAGE_NON_DEFINI",
                "Le dosage par unité de l'article " + code + " n'est pas renseigné : conversion " + uniteDose
                        + " → " + uniteDeStock + " impossible");
    }
}

