package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.util.UUID;

/**
 * Le patient d'une facture simulée : identité et état tels qu'ils seraient figés à la validation (code, nom, état).
 */
public record PatientFacture(UUID id, String code, String nom, String prenom, String etat) {

    public PatientFacture {
        if (id == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
    }

    public String codeAffiche() {
        return tiretSiVide(code);
    }

    public String nomComplet() {
        return ((nom == null ? "" : nom) + " " + (prenom == null ? "" : prenom)).trim();
    }

    public String etatAffiche() {
        return tiretSiVide(etat);
    }

    private static String tiretSiVide(String valeur) {
        return valeur == null || valeur.isBlank() ? "-" : valeur;
    }
}
