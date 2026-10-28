package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;

import java.time.LocalDate;
import java.util.List;

/**
 * Lectures des optimisations d'un centre : une exécution, l'historique paginé, les semaines et les lignes du calendrier figé de sa
 * proposition (RG-PLN-101). Une exécution d'un autre centre est introuvable ({@code OPTIMISATION_INTROUVABLE}, 404).
 */
public class ConsulterLesOptimisations {

    private final ExecutionsRepository executions;
    private final CalendriersDesPropositions calendriers;

    public ConsulterLesOptimisations(ExecutionsRepository executions, CalendriersDesPropositions calendriers) {
        this.executions = executions;
        this.calendriers = calendriers;
    }

    public static RegleDePlanificationViolee introuvable() {
        return new RegleDePlanificationViolee("OPTIMISATION_INTROUVABLE", "Optimisation introuvable");
    }

    public ExecutionDOptimisation execution(CentreId centre, ExecutionId id) {
        return executions.parId(centre, id).orElseThrow(ConsulterLesOptimisations::introuvable);
    }

    /** @throws com.hemodialyse.backend.contexts.planification.domain.PaginationInvalide page ou taille négative (400) */
    public PageDeResultats<ExecutionDOptimisation> historique(CentreId centre, Pagination pagination) {
        return executions.page(centre, pagination);
    }

    /** Débuts de semaine du calendrier ; vide pour une exécution sans résultat. */
    public List<LocalDate> semaines(CentreId centre, ExecutionId id) {
        execution(centre, id);
        return calendriers.semaines(centre, id);
    }

    /** Les lignes (salle x créneau) de la semaine qui contient cette date. */
    public List<CaseDuCalendrier> calendrier(CentreId centre, ExecutionId id, LocalDate semaine) {
        execution(centre, id);
        return calendriers.lire(centre, id, PresenceDeLaSemaine.debutDeSemaine(semaine));
    }
}
