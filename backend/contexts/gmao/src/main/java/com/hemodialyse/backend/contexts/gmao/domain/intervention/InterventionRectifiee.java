package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;

/** Une intervention terminée a été rouverte pour correction (R15). */
public record InterventionRectifiee(InterventionId interventionId, EquipementId equipementId, CentreId centreId,
                                    String motif, UtilisateurId par, Instant survenuLe) implements EvenementDIntervention {
}
