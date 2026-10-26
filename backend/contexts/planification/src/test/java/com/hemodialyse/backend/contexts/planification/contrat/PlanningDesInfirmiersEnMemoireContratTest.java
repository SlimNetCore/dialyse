package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.doubles.PlanningDesInfirmiersEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;

/**
 * Contrat du roulement, des absences et des remplacements exécuté contre les doubles en mémoire.
 */
class PlanningDesInfirmiersEnMemoireContratTest extends PlanningDesInfirmiersContrat {

    private final PlanningDesInfirmiersEnMemoire monde = new PlanningDesInfirmiersEnMemoire();

    @Override
    protected RoulementsRepository roulements() {
        return monde.roulements;
    }

    @Override
    protected AbsencesRepository absences() {
        return monde.absences;
    }

    @Override
    protected RemplacementsRepository remplacements() {
        return monde.remplacements;
    }
}
