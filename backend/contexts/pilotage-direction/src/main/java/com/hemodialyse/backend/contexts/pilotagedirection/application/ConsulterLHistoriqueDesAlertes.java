package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes.Entree;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.LimiteDHistorique;

import java.util.List;
import java.util.UUID;

/** L'historique récent des alertes de la société (en cours puis résolues), du plus récemment actif au plus ancien, borné à [1, 200]. */
public final class ConsulterLHistoriqueDesAlertes {

    private final HistoriqueDesAlertes historique;

    public ConsulterLHistoriqueDesAlertes(HistoriqueDesAlertes historique) {
        this.historique = historique;
    }

    public List<Entree> execute(UUID societeId, int limiteDemandee) {
        return historique.recentes(societeId, LimiteDHistorique.bornee(limiteDemandee).valeur());
    }
}
