package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Nom d'affichage d'un patient dans le suivi : « prénom nom » ; un prénom absent ou blanc est ignoré ; sans nom, pas de
 * nom d'affichage (patient inconnu de ce centre, KB-ABS-09).
 */
public final class NomDuPatient {

    private NomDuPatient() {
    }

    public static String complet(String prenom, String nom) {
        if (nom == null) return null;
        return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
    }
}
