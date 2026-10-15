package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

/**
 * Comptes du stock tels que l'administrateur les a saisis : tous facultatifs. Un compte absent ou blanc garde le compte actuel
 * du centre : un client qui ne connaît pas encore le stock ne le remet pas aux valeurs par défaut, et il n'existe aucun retour
 * aux comptes par défaut hors renvoi explicite de ces comptes (KB-CPT-15).
 */
public record ComptesDuStockSaisis(String stock, String consommation, String facturesNonParvenues,
                                   String boniInventaire,
                                   String maliInventaire) {

    private static String ou(String saisi, String actuel) {
        return saisi == null || saisi.isBlank() ? actuel : saisi.trim();
    }

    /**
     * Les comptes du stock après saisie : chaque compte saisi remplace l'actuel.
     */
    public ComptesDuStock surLesComptesActuels(ComptesDuStock actuels) {
        return new ComptesDuStock(ou(stock, actuels.stock()), ou(consommation, actuels.consommation()),
                ou(facturesNonParvenues, actuels.facturesNonParvenues()), ou(boniInventaire, actuels.boniInventaire()),
                ou(maliInventaire, actuels.maliInventaire()));
    }
}
