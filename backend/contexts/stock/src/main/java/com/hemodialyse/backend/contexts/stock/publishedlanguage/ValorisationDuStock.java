package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;

/**
 * Langage publié du Stock : valorisation historique d'un article (ou somme de plusieurs, {@link #plus}) sur une période, en DA à
 * 2 décimales (RG-STK-043) — valeur au début, entrées, sorties valorisées au PMP, autres variations (inventaires, ajustements sans
 * prix, arrondis), valeur et quantité à la fin.
 */
public record ValorisationDuStock(BigDecimal valeurDebut, BigDecimal entrees, BigDecimal sorties, BigDecimal autresVariations,
                                  BigDecimal valeurFin, BigDecimal quantiteFin) {

    public static ValorisationDuStock zero() {
        BigDecimal z = BigDecimal.ZERO.setScale(2);
        return new ValorisationDuStock(z, z, z, z, z, BigDecimal.ZERO);
    }

    public ValorisationDuStock plus(ValorisationDuStock o) {
        return new ValorisationDuStock(valeurDebut.add(o.valeurDebut), entrees.add(o.entrees), sorties.add(o.sorties),
                autresVariations.add(o.autresVariations), valeurFin.add(o.valeurFin), quantiteFin.add(o.quantiteFin));
    }
}
