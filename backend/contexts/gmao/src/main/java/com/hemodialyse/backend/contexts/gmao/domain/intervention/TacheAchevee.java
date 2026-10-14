package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;

/** Une tâche de l'intervention a été achevée (R21). Événement nouveau, sans équivalent legacy (KB-GMAO-55). */
public record TacheAchevee(InterventionId interventionId, TacheId tacheId, EquipementId equipementId, CentreId centreId,
                      UtilisateurId par, Instant survenuLe) implements EvenementDIntervention {
}
