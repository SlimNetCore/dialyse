package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;

/** Un plan de maintenance a été établi (actif). */
public record PlanMaintenanceEtabli(PlanMaintenanceId planId, EquipementId equipementId, CentreId centreId,
                                    FrequenceMaintenance frequence, Instant prochaineDatePrevue, UtilisateurId par,
                                    Instant survenuLe) implements EvenementDePlanMaintenance {
}
