package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;

/** Le double en mémoire passe le contrat. */
class PrescriptionsEnMemoireContratTest extends PrescriptionsContrat {

    private final Prescriptions prescriptions = new PrescriptionsEnMemoire();

    @Override
    protected Prescriptions prescriptions() {
        return prescriptions;
    }
}
