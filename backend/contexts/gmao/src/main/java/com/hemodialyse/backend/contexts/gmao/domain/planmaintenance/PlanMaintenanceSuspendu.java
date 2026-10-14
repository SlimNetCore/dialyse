package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un plan de maintenance actif a été suspendu. */
public record PlanMaintenanceSuspendu(PlanMaintenanceId planId, CentreId centreId, UtilisateurId par,
                                      Instant survenuLe) implements EvenementDePlanMaintenance {
}
