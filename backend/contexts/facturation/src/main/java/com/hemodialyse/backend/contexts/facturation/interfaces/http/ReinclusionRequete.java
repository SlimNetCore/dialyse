package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /facturation/preview/seances/{id}/include} : la période et le regroupement à recalculer, comme pour
 * l'exclusion (le motif n'y est pas utile).
 */
public record ReinclusionRequete(
        @NotNull UUID centerId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait) {

    DemandeDeSimulation demande() {
        return SimulationRequete.demande(month, periodStart, periodEnd, regroupementMultiForfait);
    }
}
