package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;

/** Une intervention a été planifiée sur un équipement. */
public record InterventionPlanifiee(InterventionId interventionId, EquipementId equipementId, CentreId centreId,
                                    TypeIntervention type, Instant debut, UtilisateurId par, Instant survenuLe)
        implements EvenementDIntervention {
}
