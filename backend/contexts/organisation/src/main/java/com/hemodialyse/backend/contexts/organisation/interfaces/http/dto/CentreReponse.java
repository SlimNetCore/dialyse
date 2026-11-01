package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;

import java.util.List;
import java.util.UUID;

/**
 * Centre tel qu'exposé dans une réponse société (mêmes champs que le legacy).
 */
public record CentreReponse(
        UUID id, String code, String nom,
        String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
        boolean actif) {

    static CentreReponse de(Centre centre) {
        Coordonnees coordonnees = centre.coordonnees();
        return new CentreReponse(centre.id().valeur(), centre.code(), centre.nom(),
                coordonnees.adresse(), coordonnees.ville(), coordonnees.wilaya(),
                coordonnees.telephone(), coordonnees.email(), coordonnees.siteWeb(),
                centre.actif());
    }
}
