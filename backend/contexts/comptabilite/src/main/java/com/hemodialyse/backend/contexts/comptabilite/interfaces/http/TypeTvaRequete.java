package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.tva.GererLesTypesDeTva;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /tva-types} et {@code PUT /tva-types/{id}} : mêmes champs et mêmes contrôles de format que le legacy
 * ({@code TvaTypeRequest}). {@code exonere} est un booléen primitif : absent, c'est une erreur interne (500, KB-FAC-10
 * reproduit). {@code userId} est l'auteur enregistré à la création.
 */
public record TypeTvaRequete(
        @NotNull UUID centerId,
        @NotBlank String libelle,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taux,
        @NotBlank String typePrestation,
        boolean exonere,
        @NotNull LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        String texteReference,
        @NotBlank String userId) {

    GererLesTypesDeTva.Saisie saisie() {
        return new GererLesTypesDeTva.Saisie(libelle, typePrestation, taux, exonere, dateDebutValidite, dateFinValidite,
                texteReference);
    }
}

