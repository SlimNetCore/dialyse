package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.doubles.PlanMaintenanceEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;

class PlanMaintenanceEnMemoireContratTest extends PlanMaintenanceContrat {

    private final PlanMaintenanceEnMemoire memoire = new PlanMaintenanceEnMemoire();

    @Override
    protected PlanMaintenanceRepository repository() {
        return memoire;
    }

    @Override
    protected LecturePlans lecture() {
        return memoire;
    }

    @Override
    protected void supprimerLogiquement(PlanMaintenanceId id) {
        memoire.supprimerLogiquement(id);
    }
}
