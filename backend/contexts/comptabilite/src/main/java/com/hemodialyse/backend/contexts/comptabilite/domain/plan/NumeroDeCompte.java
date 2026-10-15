package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Numéro d'un compte comptable : 1 à 20 lettres ou chiffres, espaces de bord rognés, enregistré en majuscules
 * (KB-CPT-04 : {@code abc9} et {@code ABC9} désignent le même compte, comme pour le code d'un journal).
 */
public record NumeroDeCompte(String valeur) implements Comparable<NumeroDeCompte> {

    public static final int LONGUEUR_MAX = 20;
    private static final Pattern FORMAT = Pattern.compile("[0-9A-Za-z]{1," + LONGUEUR_MAX + "}");

    public NumeroDeCompte {
        if (valeur == null || !FORMAT.matcher(valeur.trim()).matches()) {
            throw new NumeroDeCompteInvalide();
        }
        valeur = valeur.trim().toUpperCase(Locale.ROOT);
    }

    public static NumeroDeCompte de(String valeur) {
        return new NumeroDeCompte(valeur);
    }

    @Override
    public int compareTo(NumeroDeCompte autre) {
        return valeur.compareTo(autre.valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
