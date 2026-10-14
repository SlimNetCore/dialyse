package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/**
 * Un équipement est devenu indisponible : mise hors service par l'administrateur, ou état constaté par une intervention
 * ({@code cause}). {@code statutPrecedent} permet au consommateur de ne réagir qu'à la perte de disponibilité
 * ({@code EN_SERVICE} vers un autre statut, KB-GMAO-37).
 */
public record EquipementMisHorsService(EquipementId equipementId, CentreId centreId, TypeEquipement type, CodeEquipement code,
                                       StatutEquipement statutPrecedent, StatutEquipement statut,
                                       CauseDIndisponibilite cause, UtilisateurId par, Instant survenuLe)
        implements EvenementDEquipement {
}
