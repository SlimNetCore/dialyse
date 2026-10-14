package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;

/** Une intervention a été annulée (même si elle l'était déjà : KB-GMAO-48, reproduit). */
public record InterventionAnnulee(InterventionId interventionId, EquipementId equipementId, CentreId centreId,
                                  String raison, UtilisateurId par, Instant survenuLe) implements EvenementDIntervention {
}
