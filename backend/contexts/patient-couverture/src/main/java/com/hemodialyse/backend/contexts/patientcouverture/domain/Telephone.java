package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Téléphone normalisé : 8 à 15 chiffres après retrait des espaces, tirets et parenthèses ; {@code 00} devient {@code +} (KB-PAT-10,
 * reproduit). Vide ou blanc : pas de téléphone (voir {@link #ou(String)}).
 */
public record Telephone(String valeur) {

    public Telephone {
        if (valeur == null || valeur.isBlank()) throw new SaisieInvalide("Téléphone obligatoire");
        String brut = valeur.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
        if (brut.startsWith("00")) brut = "+" + brut.substring(2);
        String chiffres = brut.startsWith("+") ? brut.substring(1) : brut;
        if (!chiffres.matches("\\d{8,15}")) throw new SaisieInvalide("Téléphone invalide");
        valeur = brut.startsWith("+") ? "+" + chiffres : chiffres;
    }

    /** Valeur normalisée, ou nul si rien n'est saisi. */
    public static String ou(String saisie) {
        return saisie == null || saisie.isBlank() ? null : new Telephone(saisie).valeur();
    }
}
