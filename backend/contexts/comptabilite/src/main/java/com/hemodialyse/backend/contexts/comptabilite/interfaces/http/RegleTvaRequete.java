package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.tva.EnregistrerUneRegleDeTva;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corps de {@code POST /regles-tva}. {@code exonere} est obligatoire : absent ou nul, c'est une erreur interne (500, KB-CPT-19
 * reproduit). Les propriétés inconnues ({@code centerId}, {@code id}, {@code actif}) sont ignorées.
 */
public record RegleTvaRequete(
        @NotBlank String typePrestation,
        @NotNull @DecimalMin("0.00") BigDecimal tauxApplique,
        boolean exonere,
        @NotNull LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        String texteReference) {

    EnregistrerUneRegleDeTva.Commande commande() {
        return new EnregistrerUneRegleDeTva.Commande(typePrestation, tauxApplique, exonere, dateDebutValidite, dateFinValidite,
                texteReference);
    }
}
