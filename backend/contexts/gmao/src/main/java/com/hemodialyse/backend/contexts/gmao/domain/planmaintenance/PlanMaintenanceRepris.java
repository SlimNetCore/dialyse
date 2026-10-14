package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un plan de maintenance suspendu a été repris avec une nouvelle date prévue. */
public record PlanMaintenanceRepris(PlanMaintenanceId planId, CentreId centreId, Instant prochaineDatePrevue,
                                    UtilisateurId par, Instant survenuLe) implements EvenementDePlanMaintenance {
}
