package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.util.Locale;

/**
 * Code d'un journal comptable. Les journaux sont <b>paramétrables par centre</b> ({@link Journal}) : le code n'est plus
 * une liste figée. Les constantes ci-dessous sont les codes des journaux proposés par défaut à un centre qui n'a encore
 * rien paramétré.
 */
public record JournalCode(String valeur) {

    public static final int LONGUEUR_MAX = 10;
    public static final JournalCode VE = new JournalCode("VE");
    public static final JournalCode BQ = new JournalCode("BQ");
    public static final JournalCode CA = new JournalCode("CA");
    public static final JournalCode AC = new JournalCode("AC");
    public static final JournalCode ST = new JournalCode("ST");

    public JournalCode {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le code du journal est obligatoire");
        }
        valeur = valeur.trim().toUpperCase(Locale.ROOT);
        // contrôle sans champ statique : les constantes ci-dessus sont construites pendant l'initialisation de la
        // classe, et un motif déclaré après elles (ordre des champs réarrangé par un outil) serait encore nul
        if (valeur.length() > LONGUEUR_MAX || !valeur.chars().allMatch(JournalCode::lettreOuChiffre)) {
            throw new IllegalArgumentException("Le code du journal comporte 1 à " + LONGUEUR_MAX
                    + " lettres majuscules ou chiffres");
        }
    }

    private static boolean lettreOuChiffre(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    public static JournalCode de(String valeur) {
        return new JournalCode(valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
