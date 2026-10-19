package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import java.math.BigDecimal;

/**
 * Taux de TVA en pourcentage (19 pour 19 %), jamais négatif. Un taux <b>saisi</b> (création ou modification d'une règle) est
 * borné de 0 à 100 % ({@link #saisi}, règle unique de l'ADR 0030 ; KB-CPT-17 et KB-FAC-05 corrigés) ; un taux relu en base ne
 * l'est pas (les règles déjà en base ne sont pas revalidées). Aucun arrondi ici : la colonne {@code numeric(5,2)} arrondit à
 * deux décimales (reproduit).
 */
public record TauxDeTva(BigDecimal valeur) {

    private static final BigDecimal CENT = new BigDecimal("100");

    public TauxDeTva {
        if (valeur == null || valeur.signum() < 0) {
            throw new IllegalArgumentException("Le taux TVA ne peut pas être négatif");
        }
    }

    /**
     * Un taux saisi : de 0 à 100 % inclus.
     */
    public static TauxDeTva saisi(BigDecimal valeur) {
        TauxDeTva taux = new TauxDeTva(valeur);
        if (valeur.compareTo(CENT) > 0) {
            throw new IllegalArgumentException("Le taux TVA ne peut pas dépasser 100%");
        }
        return taux;
    }

    @Override
    public String toString() {
        return valeur.toString();
    }
}
