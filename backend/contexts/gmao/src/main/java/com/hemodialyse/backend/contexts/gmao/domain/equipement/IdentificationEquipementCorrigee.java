package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** L'identification d'un équipement (désignation, fabricant, localisation, salle, prix...) a été corrigée. */
public record IdentificationEquipementCorrigee(EquipementId equipementId, CentreId centreId, TypeEquipement type,
                                               SalleId salleId, UtilisateurId par, Instant survenuLe)
        implements EvenementDEquipement {
}
