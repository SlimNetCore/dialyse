package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Une maintenance préventive a été exécutée ; la prochaine date prévue est recalculée. */
public record MaintenancePreventiveExecutee(PlanMaintenanceId planId, CentreId centreId, Instant prochaineDatePrevue,
                                            int nombreExecutions, UtilisateurId par,
                                            Instant survenuLe) implements EvenementDePlanMaintenance {
}
