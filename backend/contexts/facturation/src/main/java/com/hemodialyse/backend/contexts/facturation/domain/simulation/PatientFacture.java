package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.util.UUID;

/**
 * Le patient d'une facture : identité et état tels qu'ils sont figés à la validation (code, nom, état), avec son numéro
 * d'immatriculation et son payeur (centre payeur et agence) qui figurent sur la facture émise (RG-FAC-031). Les trois derniers
 * champs sont vides tant que le patient n'a ni numéro d'assurance ni payeur.
 */
public record PatientFacture(UUID id, String code, String nom, String prenom, String etat,
                             String numeroDImmatriculation,
                             UUID centrePayeur, UUID agence) {

    public PatientFacture {
        if (id == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
    }

    /**
     * Un patient vu par la simulation, qui n'a pas besoin de son payeur.
     */
    public PatientFacture(UUID id, String code, String nom, String prenom, String etat) {
        this(id, code, nom, prenom, etat, null, null, null);
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
