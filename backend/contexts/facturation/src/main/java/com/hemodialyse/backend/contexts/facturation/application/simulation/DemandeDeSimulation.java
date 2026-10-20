package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Ce que l'utilisateur demande à la simulation : une période (un mois, ou deux dates ; le mois l'emporte) et le regroupement
 * multi-forfait (une facture par patient, sinon par patient et forfait).
 */
public record DemandeDeSimulation(YearMonth mois, LocalDate debut, LocalDate fin, boolean regroupementMultiForfait) {

    /**
     * La période demandée ; refusée (400) si elle est absente ou si la fin précède le début.
     */
    public PeriodeDeFacturation periode() {
        return PeriodeDeFacturation.de(mois, debut, fin);
    }
}
