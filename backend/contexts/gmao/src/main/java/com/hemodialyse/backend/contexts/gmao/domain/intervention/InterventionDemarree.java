package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;

/** Une intervention planifiée a démarré. */
public record InterventionDemarree(InterventionId interventionId, EquipementId equipementId, CentreId centreId,
                                   UtilisateurId par, Instant survenuLe) implements EvenementDIntervention {
}
