package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une intervention a été clôturée (première clôture ou nouvelle clôture après rectification). {@code numeroDeClôture}
 * vaut le nombre de rectifications plus un : il n'est pas stocké, il se déduit. {@code cout} est le coût total de
 * l'intervention à la clôture. Premier événement destiné à d'autres contextes (Pilotage direction) ; aucun consommateur
 * pour l'instant.
 */
public record InterventionTerminee(InterventionId interventionId, EquipementId equipementId, CentreId centreId,
                                   TypeIntervention type, Instant debut, Instant fin, BigDecimal cout, int numeroDeCloture,
                                   UtilisateurId par, Instant survenuLe) implements EvenementDIntervention {
}
