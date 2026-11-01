package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps d'une société (création et modification). L'activation transite par
 * {@code /activer} / {@code /desactiver}, jamais par ce corps : un {@code actif}
 * absent conserve l'état actuel.
 */
public record SocieteRequete(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 200) String raisonSociale,
        @Size(max = 40) String nif,
        @Size(max = 40) String nis,
        @Size(max = 40) String rc,
        @Size(max = 250) String adresse,
        @Size(max = 100) String ville,
        @Size(max = 100) String wilaya,
        @Size(max = 30) String telephone,
        @Size(max = 150) String email,
        @Size(max = 200) String siteWeb,
        @Size(max = 500) String piedDePage,
        Boolean actif) {

    public Coordonnees coordonnees() {
        return new Coordonnees(adresse, ville, wilaya, telephone, email, siteWeb);
    }
}
