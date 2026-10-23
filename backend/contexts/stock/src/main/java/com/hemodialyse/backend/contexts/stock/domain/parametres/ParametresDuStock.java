package com.hemodialyse.backend.contexts.stock.domain.parametres;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

/**
 * Port sortant : le paramétrage du stock d'un centre (table {@code stock_parametres}). Absence de ligne = valeurs par défaut.
 */
public interface ParametresDuStock {
    /**
     * Vrai si la sortie pour séances est activée pour le centre (faux par défaut).
     */
    boolean sortiePourSeancesActivee(CentreId centre);

    void definirLaSortiePourSeances(CentreId centre, boolean activee);
}