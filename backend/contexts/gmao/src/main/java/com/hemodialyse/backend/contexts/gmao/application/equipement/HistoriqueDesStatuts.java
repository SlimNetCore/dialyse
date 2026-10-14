package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.util.List;
import java.util.Map;

/**
 * Port sortant de lecture : l'historique des statuts des équipements, du changement le plus ancien au plus récent (RG-GMA-015),
 * base du calcul d'indisponibilité. Aucun filtre de centre ni de suppression : l'historique suit l'équipement.
 */
public interface HistoriqueDesStatuts {

    List<ChangementDeStatut> deLEquipement(EquipementId equipement);

    /** L'historique de chaque équipement du centre (supprimés logiquement exclus) ; un équipement sans historique est absent. */
    Map<EquipementId, List<ChangementDeStatut>> desEquipementsDuCentre(CentreId centre);
}
