package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;

/**
 * Quantité d'un consommable déclaré : strictement positive. Aucune autre borne ni précision (KB-SEA-30, reproduit : 1e12 est accepté et
 * le Stock arrondit à deux décimales sans le signaler ; bornes et décimales à décider avec les métiers). La valeur est conservée telle
 * qu'elle a été saisie (la réponse la rend à l'identique).
 */
public record QuantiteConsommee(BigDecimal valeur) {

    /** Quantité d'un ajout : le message est celui de l'ajout du legacy. */
    public static QuantiteConsommee aAjouter(BigDecimal valeur) {
        return strictementPositive(valeur, "La quantite doit etre strictement positive");
    }

    /** Nouvelle quantité totale d'une modification : le message est celui de la modification du legacy. */
    public static QuantiteConsommee nouvelle(BigDecimal valeur) {
        return strictementPositive(valeur, "La nouvelle quantite doit etre strictement positive");
    }

    private static QuantiteConsommee strictementPositive(BigDecimal valeur, String message) {
        if (valeur == null || valeur.signum() <= 0) throw new SaisieInvalide(message);
        return new QuantiteConsommee(valeur);
    }
}
