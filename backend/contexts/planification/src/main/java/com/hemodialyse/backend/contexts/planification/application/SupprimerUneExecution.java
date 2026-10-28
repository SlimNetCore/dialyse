package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;

/**
 * Supprime une exécution de l'historique du centre avec son calendrier ; un calcul en cours doit d'abord être arrêté
 * ({@code OPTIMISATION_SUPPRESSION_EN_COURS}, RG-PLN-102).
 */
public class SupprimerUneExecution {

    private final UniteDeTravail uniteDeTravail;
    private final ExecutionsRepository executions;
    private final CalendriersDesPropositions calendriers;

    public SupprimerUneExecution(UniteDeTravail uniteDeTravail, ExecutionsRepository executions,
                                 CalendriersDesPropositions calendriers) {
        this.uniteDeTravail = uniteDeTravail;
        this.executions = executions;
        this.calendriers = calendriers;
    }

    /** @throws com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee {@code OPTIMISATION_INTROUVABLE} ou {@code OPTIMISATION_SUPPRESSION_EN_COURS} */
    public void executer(CentreId centre, ExecutionId id) {
        uniteDeTravail.executer(() -> {
            ExecutionDOptimisation execution = executions.parId(centre, id).orElseThrow(ConsulterLesOptimisations::introuvable);
            execution.exigerSupprimable();
            executions.supprimer(centre, id);
            calendriers.purgerOrphelins(centre);
            return null;
        });
    }
}
