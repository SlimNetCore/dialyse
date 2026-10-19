package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.parametres.ConfigurerLaFacturation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corps de {@code PUT /facturation/settings} : mêmes champs et mêmes contrôles de format que le legacy
 * ({@code FacturationSettingsUpdateRequest}). {@code regroupementMultiForfait} est un booléen primitif : absent, c'est une
 * erreur interne (500, KB-FAC-10 reproduit). {@code userId} est l'auteur enregistré.
 */
public record ParametresFacturationRequete(
        @NotNull UUID centerId,
        @NotBlank String userId,
        @NotBlank String codeFormat,
        boolean regroupementMultiForfait) {

    ConfigurerLaFacturation.Commande commande() {
        return new ConfigurerLaFacturation.Commande(codeFormat, regroupementMultiForfait);
    }
}
