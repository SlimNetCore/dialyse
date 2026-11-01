package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Société telle qu'exposée par l'API (mêmes champs que le legacy).
 */
public record SocieteReponse(
        UUID id, String code, String raisonSociale, String nif, String nis, String rc,
        String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
        String piedDePage, boolean hasLogo, boolean actif, OffsetDateTime createdAt,
        List<CentreReponse> centres) {

    public static SocieteReponse de(Societe societe, List<Centre> centres, boolean logo) {
        Coordonnees coordonnees = societe.coordonnees();
        return new SocieteReponse(societe.id().valeur(), societe.code(), societe.raisonSociale(),
                societe.nif(), societe.nis(), societe.rc(),
                coordonnees.adresse(), coordonnees.ville(), coordonnees.wilaya(),
                coordonnees.telephone(), coordonnees.email(), coordonnees.siteWeb(),
                societe.piedDePage(), logo, societe.actif(), societe.createdAt(),
                centres.stream().map(CentreReponse::de).toList());
    }
}
