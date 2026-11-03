package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.UUID;

/**
 * Port publié (implémenté hors du contexte, par la plateforme) : prévient les écrans connectés qu'une fiche est créée ou
 * modifiée (diffusions {@code PATIENT_*}). Termes minimaux : identifiants nus, aucun type du modèle interne.
 */
public interface NotificationsDeLaFiche {

    void ficheCreee(UUID centre, UUID patient, String code, String nom, String prenom);

    void ficheModifiee(UUID centre, UUID patient, String code, String nom, String prenom);
}
