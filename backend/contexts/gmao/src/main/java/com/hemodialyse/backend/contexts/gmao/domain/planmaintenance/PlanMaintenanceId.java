package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import java.util.UUID;

public record PlanMaintenanceId(UUID valeur) {
    public PlanMaintenanceId {
        if (valeur == null) throw new IllegalArgumentException("Plan de maintenance requis");
    }
}
