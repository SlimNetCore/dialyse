package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

/**
 * Le numéro n'a pas 1 à 20 lettres ou chiffres (400 observé).
 */
public class NumeroDeCompteInvalide extends IllegalArgumentException {
    public NumeroDeCompteInvalide() {
        super("Un compte comporte 1 à " + NumeroDeCompte.LONGUEUR_MAX + " lettres ou chiffres");
    }
}
