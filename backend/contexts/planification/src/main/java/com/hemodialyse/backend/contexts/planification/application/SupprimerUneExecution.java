package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;

/**
 * Supprime une exécution de l'historique du centre avec son calendrier ; un calcul en cours doit d'abord être arrêté
 * ({@code OPTIMISATION_SUPPRESSION_EN_COURS}, RG-PLN-102).
 */
public class SupprimerUneExecution {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;
    private final ExecutionsRepository executions;
    private final CalendriersDesPropositions calendriers;

    public SupprimerUneExecution(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou, ExecutionsRepository executions,
                                 CalendriersDesPropositions calendriers) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.executions = executions;
        this.calendriers = calendriers;
    }

    /** @throws com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee {@code OPTIMISATION_INTROUVABLE} ou {@code OPTIMISATION_SUPPRESSION_EN_COURS} */
    public void executer(CentreId centre, ExecutionId id) {
        uniteDeTravail.executer(() -> {
            verrou.prendre(centre); // exclusion avec une application ou un lancement du centre (m3 de la revue P8)
            ExecutionDOptimisation execution = executions.parId(centre, id).orElseThrow(ConsulterLesOptimisations::introuvable);
            execution.exigerSupprimable();
            executions.supprimer(centre, id);
            calendriers.purgerOrphelins(centre);
            return null;
        });
    }
}
