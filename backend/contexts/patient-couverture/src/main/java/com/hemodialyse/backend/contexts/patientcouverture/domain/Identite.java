package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

/** État civil du patient. Nom, prénom, sexe et date de naissance sont obligatoires. */
public record Identite(String civilite, String nom, String prenom, String sexe, String groupeSanguin, int nombreEnfants,
                       LocalDate dateNaissance, String lieuNaissance, String situationFamiliale) {

    public Identite {
        if (nom == null || nom.isBlank()) throw new SaisieInvalide("Nom obligatoire");
        if (prenom == null || prenom.isBlank()) throw new SaisieInvalide("Prenom obligatoire");
        if (sexe == null || sexe.isBlank()) throw new SaisieInvalide("Sexe obligatoire");
        if (dateNaissance == null) throw new SaisieInvalide("Date naissance obligatoire");
    }
}
