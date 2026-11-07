package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;

/** Le double en mémoire passe le contrat. */
class AbordsVasculairesEnMemoireContratTest extends AbordsVasculairesContrat {
    private final AbordsVasculaires abords = new AbordsVasculairesEnMemoire();

    @Override
    protected AbordsVasculaires abords() {
        return abords;
    }
}
