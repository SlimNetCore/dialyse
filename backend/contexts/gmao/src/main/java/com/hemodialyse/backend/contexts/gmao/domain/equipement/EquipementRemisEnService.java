package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un équipement est revenu en service (remise en service, ou état constaté par une intervention). */
public record EquipementRemisEnService(EquipementId equipementId, CentreId centreId, TypeEquipement type, CodeEquipement code,
                                       StatutEquipement statutPrecedent, UtilisateurId par, Instant survenuLe)
        implements EvenementDEquipement {
}
