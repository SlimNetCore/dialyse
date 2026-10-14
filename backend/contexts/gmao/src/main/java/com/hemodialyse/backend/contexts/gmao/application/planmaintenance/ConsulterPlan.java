package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;

import java.util.Optional;

/**
 * Consulte un plan du centre par identité. Un plan d'un autre centre est traité comme inexistant (KB-GMAO-15 corrigé) ;
 * la suppression logique n'est toujours pas filtrée (KB-GMAO-17).
 */
public final class ConsulterPlan {

    private final LecturePlans lecture;

    public ConsulterPlan(LecturePlans lecture) {
        this.lecture = lecture;
    }

    public Optional<PlanMaintenanceVue> executer(CentreId centre, PlanMaintenanceId id) {
        return lecture.parId(id).filter(plan -> plan.centreId().equals(centre.valeur()));
    }
}
