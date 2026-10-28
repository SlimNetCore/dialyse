package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;

/**
 * Demande l'arrêt anticipé d'un calcul en cours : la meilleure solution trouvée est conservée (RG-PLN-090). Rend l'exécution telle
 * qu'elle était AVANT l'arrêt (le solveur répond ensuite) ; sans effet sur un calcul terminé (KB-PLN-99).
 */
public class ArreterUneOptimisation {

    private final ExecutionsRepository executions;
    private final Optimiseur optimiseur;

    public ArreterUneOptimisation(ExecutionsRepository executions, Optimiseur optimiseur) {
        this.executions = executions;
        this.optimiseur = optimiseur;
    }

    /** @throws com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee {@code OPTIMISATION_INTROUVABLE} */
    public ExecutionDOptimisation executer(CentreId centre, ExecutionId id) {
        ExecutionDOptimisation execution = executions.parId(centre, id).orElseThrow(ConsulterLesOptimisations::introuvable);
        if (execution.enCours()) optimiseur.arreter(id);
        return execution;
    }
}
