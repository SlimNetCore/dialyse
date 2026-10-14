package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un équipement a été réformé : état terminal, aucun événement ultérieur pour cet équipement. */
public record EquipementReforme(EquipementId equipementId, CentreId centreId, TypeEquipement type, CodeEquipement code,
                                StatutEquipement statutPrecedent, UtilisateurId par, Instant survenuLe)
        implements EvenementDEquipement {
}
