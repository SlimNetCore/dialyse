package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Adapter JPA du read model {@link LecturePlans} (requêtes JPQL équivalentes à celles du legacy). */
public class LecturePlansJpa implements LecturePlans {

    private final PlanMaintenanceSpringData donnees;

    public LecturePlansJpa(PlanMaintenanceSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public Optional<PlanMaintenanceVue> parId(PlanMaintenanceId id) {
        return donnees.findById(id.valeur()).map(PlanMaintenanceMapper::versVue);
    }

    @Override
    public List<PlanMaintenanceVue> duCentre(CentreId centre) {
        return donnees.duCentre(centre.valeur()).stream().map(PlanMaintenanceMapper::versVue).toList();
    }

    @Override
    public List<PlanMaintenanceVue> actifsDuCentre(CentreId centre) {
        return donnees.actifsDuCentre(centre.valeur()).stream().map(PlanMaintenanceMapper::versVue).toList();
    }

    @Override
    public List<PlanMaintenanceVue> enRetardDuCentre(CentreId centre, Instant maintenant) {
        return donnees.enRetardDuCentre(centre.valeur(), maintenant).stream().map(PlanMaintenanceMapper::versVue).toList();
    }

    @Override
    public List<PlanMaintenanceVue> deEquipement(EquipementId equipement) {
        return donnees.deEquipement(equipement.valeur()).stream().map(PlanMaintenanceMapper::versVue).toList();
    }
}
