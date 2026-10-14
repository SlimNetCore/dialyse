package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.util.List;

/** Liste les plans en retard du centre (R27 : actifs dont la prochaine date prévue est atteinte). */
public final class ListerPlansEnRetard {

    private final LecturePlans lecture;
    private final Horloge horloge;

    public ListerPlansEnRetard(LecturePlans lecture, Horloge horloge) {
        this.lecture = lecture;
        this.horloge = horloge;
    }

    public List<PlanMaintenanceVue> executer(CentreId centre) {
        return lecture.enRetardDuCentre(centre, horloge.maintenant());
    }
}
