package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.util.List;

/** Liste les plans actifs du centre. */
public final class ListerPlansActifs {

    private final LecturePlans lecture;

    public ListerPlansActifs(LecturePlans lecture) {
        this.lecture = lecture;
    }

    public List<PlanMaintenanceVue> executer(CentreId centre) {
        return lecture.actifsDuCentre(centre);
    }
}
