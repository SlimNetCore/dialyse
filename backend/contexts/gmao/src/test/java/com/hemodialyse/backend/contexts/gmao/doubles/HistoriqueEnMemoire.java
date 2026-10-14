package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Double in-memory du port {@link HistoriqueDesStatuts} : l'historique tenu par le {@link ParcEnMemoire}. */
public class HistoriqueEnMemoire implements HistoriqueDesStatuts {

    private final ParcEnMemoire parc;

    public HistoriqueEnMemoire(ParcEnMemoire parc) {
        this.parc = parc;
    }

    @Override
    public List<ChangementDeStatut> deLEquipement(EquipementId equipement) {
        return parc.historiqueDe(equipement);
    }

    @Override
    public Map<EquipementId, List<ChangementDeStatut>> desEquipementsDuCentre(CentreId centre) {
        Map<EquipementId, List<ChangementDeStatut>> historiques = new HashMap<>();
        parc.parcDuCentre(centre).forEach(e -> {
            List<ChangementDeStatut> historique = parc.historiqueDe(new EquipementId(e.id()));
            if (!historique.isEmpty()) historiques.put(new EquipementId(e.id()), historique);
        });
        return historiques;
    }
}
