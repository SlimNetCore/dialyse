package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import java.util.Optional;

/** Port sortant : persistance de l'agrégat {@link PlanMaintenance} (un repository par racine). */
public interface PlanMaintenanceRepository {

    void enregistrer(PlanMaintenance plan);

    Optional<PlanMaintenance> trouverParId(PlanMaintenanceId id);
}
