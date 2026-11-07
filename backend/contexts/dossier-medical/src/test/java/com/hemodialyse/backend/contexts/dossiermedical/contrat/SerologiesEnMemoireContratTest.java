package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;

/** Le double en mémoire passe le contrat. */
class SerologiesEnMemoireContratTest extends SerologiesContrat {

    private final Serologies serologies = new SerologiesEnMemoire();

    @Override
    protected Serologies serologies() {
        return serologies;
    }
}
