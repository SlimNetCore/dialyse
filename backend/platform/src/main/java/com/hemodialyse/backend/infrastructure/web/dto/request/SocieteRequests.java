package com.hemodialyse.backend.infrastructure.web.dto.request;

import com.hemodialyse.backend.domain.organisation.model.Coordonnees;
import com.hemodialyse.backend.domain.organisation.port.SocieteUseCase.CentreData;
import com.hemodialyse.backend.domain.organisation.port.SocieteUseCase.SocieteData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Requêtes de gestion des sociétés et de leurs centres (SUPERADMIN). Les règles métier (formats, codes, invariant
 * « au moins un centre ») sont vérifiées par le domaine ; ici seules les bornes de taille protègent le transport.
 */
public final class SocieteRequests {

    private SocieteRequests() {
    }

    public record SocieteRequest(
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
            @Size(max = 500) String piedDePage) {

        public SocieteData toData() {
            return new SocieteData(code, raisonSociale, nif, nis, rc,
                    new Coordonnees(adresse, ville, wilaya, telephone, email, siteWeb), piedDePage);
        }
    }

    public record CentreRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 150) String nom,
            @Size(max = 250) String adresse,
            @Size(max = 100) String ville,
            @Size(max = 100) String wilaya,
            @Size(max = 30) String telephone,
            @Size(max = 150) String email,
            @Size(max = 200) String siteWeb) {

        public CentreData toData() {
            return new CentreData(code, nom, new Coordonnees(adresse, ville, wilaya, telephone, email, siteWeb));
        }
    }

    /**
     * Une société ne se crée qu'avec son premier centre.
     */
    public record CreateSocieteRequest(@NotNull @Valid SocieteRequest societe,
                                       @NotNull @Valid CentreRequest premierCentre) {
    }

    public record TransfertCentreRequest(@NotNull UUID societeCibleId) {
    }
}
