package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Corps de {@code POST /facturation/preview} : mêmes champs que le legacy ({@code FacturationPreviewRequest}).
 * {@code regroupementMultiForfait} est un booléen primitif : absent, c'est une erreur interne (500, KB-FAC-19 reproduit) ; un
 * mois illisible aussi.
 */
public record SimulationRequete(
        @NotNull UUID centerId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait) {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyy-MM");

    DemandeDeSimulation demande() {
        return demande(month, periodStart, periodEnd, regroupementMultiForfait);
    }

    static DemandeDeSimulation demande(String month, LocalDate periodStart, LocalDate periodEnd, boolean regroupementMultiForfait) {
        YearMonth mois = month == null || month.isBlank() ? null : YearMonth.parse(month.trim(), MOIS);
        return new DemandeDeSimulation(mois, periodStart, periodEnd, regroupementMultiForfait);
    }
}
