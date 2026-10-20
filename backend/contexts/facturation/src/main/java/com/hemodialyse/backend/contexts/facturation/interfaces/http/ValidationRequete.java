package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /facturation/validate} : mêmes champs que le legacy ({@code FacturationValidateRequest}). {@code userId}
 * reste obligatoire mais l'auteur de l'émission est l'utilisateur de la session ; {@code previewGeneratedAt} est ignoré
 * (KB-FAC-30). {@code regroupementMultiForfait} est un booléen primitif : absent, c'est une erreur interne (500, KB-FAC-35
 * reproduit), comme un mois illisible.
 */
public record ValidationRequete(
        @NotNull UUID centerId,
        @NotBlank String userId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait,
        String previewGeneratedAt) {

    DemandeDeSimulation demande() {
        return SimulationRequete.demande(month, periodStart, periodEnd, regroupementMultiForfait);
    }
}
