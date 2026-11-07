package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;

/** Le double en mémoire passe le contrat. */
class BilansPreGreffeEnMemoireContratTest extends BilansPreGreffeContrat {
    private final BilansPreGreffe bilans = new BilansPreGreffeEnMemoire();

    @Override
    protected BilansPreGreffe bilans() {
        return bilans;
    }
}
