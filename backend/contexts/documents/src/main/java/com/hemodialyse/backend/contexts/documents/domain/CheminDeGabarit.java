package com.hemodialyse.backend.contexts.documents.domain;

import java.util.regex.Pattern;

/**
 * Le gabarit d'un modèle de document : uniquement un gabarit livré avec l'application, {@code reports/<nom>.jrxml} où le nom est fait de
 * lettres sans accent, chiffres, tiret et tiret bas. Jamais un chemin disque libre : c'est ce qui empêche un modèle de désigner un fichier
 * du serveur. Reprise à l'identique de la règle de l'ancien contrôleur ({@code SAFE_TEMPLATE_PATH}).
 */
public record CheminDeGabarit(String valeur) {

    private static final Pattern GABARIT_LIVRE = Pattern.compile("reports/[A-Za-z0-9_\\-]+\\.jrxml");

    public CheminDeGabarit {
        if (valeur == null || !GABARIT_LIVRE.matcher(valeur).matches()) {
            throw new ModeleRefuse("Chemin de modèle invalide : attendu reports/<nom>.jrxml");
        }
    }

    public static CheminDeGabarit de(String valeur) {
        return new CheminDeGabarit(valeur);
    }
}
