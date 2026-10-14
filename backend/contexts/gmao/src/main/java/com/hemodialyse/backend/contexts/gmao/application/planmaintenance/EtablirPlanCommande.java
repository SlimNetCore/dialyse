package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;

import java.time.Instant;

public record EtablirPlanCommande(EquipementId equipementId, CentreId centreId, String designation, String description,
                                  FrequenceMaintenance frequence, Instant prochaineDatePrevue,
                                  String tachesAEffectuer, UtilisateurId par) {
}
