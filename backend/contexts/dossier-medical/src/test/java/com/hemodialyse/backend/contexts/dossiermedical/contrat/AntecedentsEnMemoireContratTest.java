package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;

/** Le double en mémoire passe le contrat. */
class AntecedentsEnMemoireContratTest extends AntecedentsContrat {
    private final Antecedents antecedents = new AntecedentsEnMemoire();

    @Override
    protected Antecedents antecedents() {
        return antecedents;
    }
}
