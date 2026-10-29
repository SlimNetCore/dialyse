package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;

/**
 * Le double en mémoire de l'optimisation passe le MÊME contrat que l'adapter JDBC ({@code OptimisationJdbcContratTest},
 * {@code OptimisationJdbcPostgresContractIT}) : il est donc fidèle.
 */
class OptimisationEnMemoireContratTest extends OptimisationContrat {

    private final OptimisationEnMemoire monde = new OptimisationEnMemoire(new com.hemodialyse.backend.contexts.planification.application.UniteDeTravail() {
        @Override
        public <T> T executer(java.util.function.Supplier<T> action) {
            return action.get();
        }
    });

    @Override
    ExecutionsRepository executionsDeLInstance(String instance) {
        return monde.executionsPour(instance);
    }

    @Override
    DeplacementsTemporairesRepository deplacements() {
        return monde.deplacements;
    }

    @Override
    CalendriersDesPropositions calendriers() {
        return monde.calendriers;
    }

    @Override
    void nettoyer() {
        monde.executions.clear();
        monde.temporaires.clear();
        monde.calendriersEnregistres.clear();
    }
}
