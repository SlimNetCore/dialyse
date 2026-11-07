package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;

/** Le double en mémoire passe le contrat. */
class AnalysesEnMemoireContratTest extends AnalysesContrat {
    private final Analyses analyses = new AnalysesEnMemoire();

    @Override
    protected Analyses analyses() {
        return analyses;
    }
}
