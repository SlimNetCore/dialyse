package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Création d'une société : toujours avec son premier centre (RG-ORG-001).
 */
public record CreerSocieteRequete(
        @NotNull @Valid SocieteRequete societe,
        @NotNull @Valid CentreRequete premierCentre) {
}
