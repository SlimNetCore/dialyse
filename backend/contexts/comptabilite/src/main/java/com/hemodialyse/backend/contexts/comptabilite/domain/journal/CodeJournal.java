package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import java.util.Locale;

/**
 * Code d'un journal comptable : 1 à 10 lettres majuscules ou chiffres ; les minuscules et les espaces de bord sont
 * normalisés. Les journaux sont paramétrables par centre : les constantes sont les codes proposés par défaut.
 */
public record CodeJournal(String valeur) implements Comparable<CodeJournal> {

    public static final int LONGUEUR_MAX = 10;
    public static final CodeJournal VE = new CodeJournal("VE");
    public static final CodeJournal BQ = new CodeJournal("BQ");
    public static final CodeJournal CA = new CodeJournal("CA");
    public static final CodeJournal AC = new CodeJournal("AC");
    public static final CodeJournal ST = new CodeJournal("ST");

    public CodeJournal {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le code du journal est obligatoire");
        }
        valeur = valeur.trim().toUpperCase(Locale.ROOT);
        // contrôle sans champ statique : les constantes ci-dessus sont construites pendant l'initialisation de la classe
        if (valeur.length() > LONGUEUR_MAX || !valeur.chars().allMatch(CodeJournal::lettreOuChiffre)) {
            throw new IllegalArgumentException("Le code du journal comporte 1 à " + LONGUEUR_MAX
                    + " lettres majuscules ou chiffres");
        }
    }

    private static boolean lettreOuChiffre(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    public static CodeJournal de(String valeur) {
        return new CodeJournal(valeur);
    }

    @Override
    public int compareTo(CodeJournal autre) {
        return valeur.compareTo(autre.valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
