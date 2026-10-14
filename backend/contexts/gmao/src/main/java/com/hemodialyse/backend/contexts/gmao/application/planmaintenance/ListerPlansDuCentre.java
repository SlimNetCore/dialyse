package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.util.List;

/** Liste tous les plans du centre (actifs, suspendus, archivés). */
public final class ListerPlansDuCentre {

    private final LecturePlans lecture;

    public ListerPlansDuCentre(LecturePlans lecture) {
        this.lecture = lecture;
    }

    public List<PlanMaintenanceVue> executer(CentreId centre) {
        return lecture.duCentre(centre);
    }
}
