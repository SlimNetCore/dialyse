package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;

/** Le double en mémoire passe le contrat. */
class AlertesEnMemoireContratTest extends AlertesContrat {

    private final Alertes alertes = new AlertesEnMemoire();

    @Override
    protected Alertes alertes() {
        return alertes;
    }
}
