package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.Locale;

/**
 * Numéro d'assurance du patient, normalisé (espaces de bord retirés, majuscules) : {@code pc1-doublon} et {@code PC1-DOUBLON }
 * désignent le même numéro (décision du 02/11, KB-PAT-03). Unique par centre.
 */
public record NumeroAssurance(String valeur) {
    public NumeroAssurance {
        if (valeur == null || valeur.isBlank()) throw new SaisieInvalide("Numero assurance obligatoire");
        valeur = valeur.trim().toUpperCase(Locale.ROOT);
    }
}
