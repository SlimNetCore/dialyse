package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;

/**
 * Fiche saisie : tout est lu comme du texte, les valeurs sont jugées par le domaine (400 si inutilisables).
 */
public record InfirmierDemande(String matricule, String nom, String prenom, String telephone, String qualification,
                               Boolean habiliteIsolement) {
    public IdentiteDeLInfirmier identite() {
        return new IdentiteDeLInfirmier(nom, prenom, telephone);
    }

    public boolean habilite() {
        return Boolean.TRUE.equals(habiliteIsolement);
    }
}
