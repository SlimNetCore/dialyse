package com.hemodialyse.backend.contexts.stock.application.parametres;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.parametres.ParametresDuStock;

/**
 * Cas d'usage du paramétrage du stock d'un centre : bascule de la sortie pour séances (ADR 0045). Désactivé par défaut ; tant qu'il
 * l'est, la séance garde la sortie immédiate FEFO. Retour arrière : désactiver (les pièces déjà enregistrées restent à rapprocher).
 */
public final class ParametrerLeStock {
    private final ParametresDuStock parametres;

    public ParametrerLeStock(ParametresDuStock parametres) {
        this.parametres = parametres;
    }

    public boolean sortiePourSeancesActivee(CentreId centre) {
        return parametres.sortiePourSeancesActivee(centre);
    }

    public void activerLaSortiePourSeances(CentreId centre, boolean activee) {
        parametres.definirLaSortiePourSeances(centre, activee);
    }
}