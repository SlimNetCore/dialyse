package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

/**
 * Modification d'un médecin référencé.
 */
public record ModifierMedecinRequete(String nom, String prenom, String specialite) {
}
