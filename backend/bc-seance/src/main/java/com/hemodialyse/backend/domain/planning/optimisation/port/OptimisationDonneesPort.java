package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port de lecture des données à optimiser, toujours bornées au centre (AGENTS.md §2).
 */
public interface OptimisationDonneesPort {

    /**
     * @param du premier jour de l'horizon (inclus) : absences, remplacements et fermetures sont limités à l'horizon
     * @param au dernier jour de l'horizon (inclus)
     */
    DonneesOptimisation charger(UUID centerId, LocalDate du, LocalDate au);
}
