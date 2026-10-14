package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.util.List;

/** Liste les plans d'un équipement pour le centre, quel que soit le statut ; les plans d'un autre centre sont ignorés (KB-GMAO-15 corrigé). */
public final class ListerPlansDEquipement {

    private final LecturePlans lecture;

    public ListerPlansDEquipement(LecturePlans lecture) {
        this.lecture = lecture;
    }

    public List<PlanMaintenanceVue> executer(CentreId centre, EquipementId equipement) {
        return lecture.deEquipement(equipement).stream()
                .filter(plan -> plan.centreId().equals(centre.valeur()))
                .toList();
    }
}
