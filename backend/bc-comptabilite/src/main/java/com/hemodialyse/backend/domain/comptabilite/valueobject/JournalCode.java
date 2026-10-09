package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.util.Locale;
import java.util.regex.Pattern;

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
    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]{1," + LONGUEUR_MAX + "}");

    public JournalCode {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le code du journal est obligatoire");
        }
        valeur = valeur.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(valeur).matches()) {
            throw new IllegalArgumentException("Le code du journal comporte 1 à " + LONGUEUR_MAX
                    + " lettres majuscules ou chiffres");
        }
    }

    public static JournalCode de(String valeur) {
        return new JournalCode(valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
