package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

/**
 * État civil du patient. À la saisie ({@link #saisie}) nom, prénom, sexe et date de naissance sont obligatoires ; une ligne
 * héritée relue de la base peut en manquer (colonnes nulles) : le constructeur ne contrôle rien.
 */
public record Identite(String civilite, String nom, String prenom, String sexe, String groupeSanguin, int nombreEnfants,
                       LocalDate dateNaissance, String lieuNaissance, String situationFamiliale) {

    public static Identite saisie(String civilite, String nom, String prenom, String sexe, String groupeSanguin,
                                  int nombreEnfants, LocalDate dateNaissance, String lieuNaissance,
                                  String situationFamiliale) {
        if (nom == null || nom.isBlank()) throw new SaisieInvalide("Nom obligatoire");
        if (prenom == null || prenom.isBlank()) throw new SaisieInvalide("Prenom obligatoire");
        if (sexe == null || sexe.isBlank()) throw new SaisieInvalide("Sexe obligatoire");
        if (dateNaissance == null) throw new SaisieInvalide("Date naissance obligatoire");
        return new Identite(civilite, nom, prenom, sexe, groupeSanguin, nombreEnfants, dateNaissance, lieuNaissance,
                situationFamiliale);
    }
}
