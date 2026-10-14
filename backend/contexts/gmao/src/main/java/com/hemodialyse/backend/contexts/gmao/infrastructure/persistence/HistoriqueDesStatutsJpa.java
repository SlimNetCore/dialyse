package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapter JPA du read model {@link HistoriqueDesStatuts} : table {@code gmao_equipement_statut_historique}, du plus ancien au plus récent. */
public class HistoriqueDesStatutsJpa implements HistoriqueDesStatuts {

    private final EquipementStatutHistoriqueSpringData donnees;

    public HistoriqueDesStatutsJpa(EquipementStatutHistoriqueSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public List<ChangementDeStatut> deLEquipement(EquipementId equipement) {
        return donnees.duPlusAncienAuPlusRecent(equipement.valeur()).stream().map(HistoriqueDesStatutsJpa::versChangement).toList();
    }

    @Override
    public Map<EquipementId, List<ChangementDeStatut>> desEquipementsDuCentre(CentreId centre) {
        Map<EquipementId, List<ChangementDeStatut>> parEquipement = new HashMap<>();
        for (EquipementStatutHistoriqueJpa ligne : donnees.duCentre(centre.valeur())) {
            parEquipement.computeIfAbsent(new EquipementId(ligne.getEquipementId()), id -> new ArrayList<>())
                    .add(versChangement(ligne));
        }
        return parEquipement;
    }

    private static ChangementDeStatut versChangement(EquipementStatutHistoriqueJpa ligne) {
        return new ChangementDeStatut(
                ligne.getStatutPrecedent() == null ? null : StatutEquipement.valueOf(ligne.getStatutPrecedent()),
                StatutEquipement.valueOf(ligne.getStatutNouveau()), ligne.getMotif(),
                ligne.getChangedBy() == null ? null : new UtilisateurId(ligne.getChangedBy()), ligne.getChangedAt());
    }
}
