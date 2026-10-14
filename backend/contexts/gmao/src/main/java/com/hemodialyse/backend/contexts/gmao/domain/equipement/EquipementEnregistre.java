package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un équipement a été enregistré (en service) dans le parc du centre. */
public record EquipementEnregistre(EquipementId equipementId, CentreId centreId, TypeEquipement type, CodeEquipement code,
                                   SalleId salleId, UtilisateurId par, Instant survenuLe) implements EvenementDEquipement {
}
