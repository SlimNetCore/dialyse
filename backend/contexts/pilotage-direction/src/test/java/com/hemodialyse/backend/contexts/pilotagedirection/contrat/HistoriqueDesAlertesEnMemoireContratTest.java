package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertesEnMemoire;

/** Le double en mémoire respecte le contrat de l'historique des alertes. */
class HistoriqueDesAlertesEnMemoireContratTest extends HistoriqueDesAlertesContrat {

    private final HistoriqueDesAlertesEnMemoire historique = new HistoriqueDesAlertesEnMemoire();

    @Override
    protected HistoriqueDesAlertes historique() {
        return historique;
    }
}
