package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import java.math.BigDecimal;

/**
 * Taux de TVA en pourcentage (19 pour 19 %). Borné par zéro seulement : aucune borne haute et aucun arrondi ici, la colonne
 * arrondit à deux décimales et refuse 1000 et plus (KB-CPT-17 reproduit ; à trancher avec la modélisation de la Facturation).
 */
public record TauxDeTva(BigDecimal valeur) {

    public TauxDeTva {
        if (valeur == null || valeur.signum() < 0) {
            throw new IllegalArgumentException("Le taux TVA ne peut pas être négatif");
        }
    }

    @Override
    public String toString() {
        return valeur.toString();
    }
}
