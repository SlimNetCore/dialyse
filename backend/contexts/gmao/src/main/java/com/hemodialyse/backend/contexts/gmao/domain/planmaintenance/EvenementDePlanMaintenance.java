package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.time.Instant;

/** Événement de domaine d'un plan de maintenance (nom au passé, immuable). */
public interface EvenementDePlanMaintenance {

    PlanMaintenanceId planId();

    CentreId centreId();

    Instant survenuLe();
}
