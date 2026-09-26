package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.organisation.model.Centre;
import com.hemodialyse.backend.domain.organisation.model.Coordonnees;
import com.hemodialyse.backend.domain.organisation.model.Societe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SocieteResponse(
        UUID id, String code, String raisonSociale, String nif, String nis, String rc,
        String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
        String piedDePage, boolean hasLogo, boolean actif, OffsetDateTime createdAt, List<CentreResponse> centres) {

    public static SocieteResponse from(Societe s) {
        Coordonnees k = s.coordonnees();
        return new SocieteResponse(s.id(), s.code(), s.raisonSociale(), s.nif(), s.nis(), s.rc(),
                k.adresse(), k.ville(), k.wilaya(), k.telephone(), k.email(), k.siteWeb(),
                s.piedDePage(), false, s.actif(), s.createdAt(), s.centres().stream().map(CentreResponse::from).toList());
    }

    public SocieteResponse withLogo(boolean logo) {
        return new SocieteResponse(id, code, raisonSociale, nif, nis, rc, adresse, ville, wilaya, telephone, email,
                siteWeb, piedDePage, logo, actif, createdAt, centres);
    }

    public record CentreResponse(
            UUID id, String code, String nom,
            String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
            boolean actif) {

        static CentreResponse from(Centre c) {
            Coordonnees k = c.coordonnees();
            return new CentreResponse(c.id(), c.code(), c.nom(), k.adresse(), k.ville(), k.wilaya(),
                    k.telephone(), k.email(), k.siteWeb(), c.actif());
        }
    }
}
