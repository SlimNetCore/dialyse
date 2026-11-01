package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps d'un centre (création et modification).
 */
public record CentreRequete(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 250) String adresse,
        @Size(max = 100) String ville,
        @Size(max = 100) String wilaya,
        @Size(max = 30) String telephone,
        @Size(max = 150) String email,
        @Size(max = 200) String siteWeb) {

    public Coordonnees coordonnees() {
        return new Coordonnees(adresse, ville, wilaya, telephone, email, siteWeb);
    }
}
