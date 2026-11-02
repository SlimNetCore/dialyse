package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Courriel normalisé en minuscules (KB-PAT-10, reproduit : {@code a@b..co} reste accepté). */
public record Courriel(String valeur) {

    private static final Pattern FORME = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    public Courriel {
        if (valeur == null || valeur.isBlank()) throw new SaisieInvalide("Email obligatoire");
        String normalise = valeur.trim().toLowerCase(Locale.ROOT);
        if (!FORME.matcher(normalise).matches()) throw new SaisieInvalide("Email invalide");
        valeur = normalise;
    }

    /** Valeur normalisée, ou nul si rien n'est saisi. */
    public static String ou(String saisie) {
        return saisie == null || saisie.isBlank() ? null : new Courriel(saisie).valeur();
    }
}
