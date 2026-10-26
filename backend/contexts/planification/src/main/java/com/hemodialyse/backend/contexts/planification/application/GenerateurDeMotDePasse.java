package com.hemodialyse.backend.contexts.planification.application;

/**
 * Port sortant : génère le mot de passe temporaire d'un compte créé depuis une fiche (aléa hors du domaine).
 */
public interface GenerateurDeMotDePasse {
    String generer();
}
