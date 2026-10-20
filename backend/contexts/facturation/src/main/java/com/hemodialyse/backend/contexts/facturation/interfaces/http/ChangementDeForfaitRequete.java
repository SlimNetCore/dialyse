package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /facturation/preview/seances/{id}/forfait} : mêmes champs que le legacy
 * ({@code FacturationPreviewUpdateForfaitRequest}). {@code userId} est l'auteur de la surcharge.
 */
public record ChangementDeForfaitRequete(
        @NotNull UUID centerId,
        @NotBlank String userId,
        @NotNull UUID forfaitId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait) {

    DemandeDeSimulation demande() {
        return SimulationRequete.demande(month, periodStart, periodEnd, regroupementMultiForfait);
    }
}
