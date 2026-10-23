package com.hemodialyse.backend.contexts.stock.domain.commandes;

/**
 * Ligne de bon de commande inutilisable (article absent, quantité nulle ou négative, prix négatif) : 400
 * {@code BAD_REQUEST} (KB-STK-19 et 20 corrigés).
 */
public class BonDeCommandeInvalide extends RuntimeException {

    public BonDeCommandeInvalide(String message) {
        super(message);
    }
}

