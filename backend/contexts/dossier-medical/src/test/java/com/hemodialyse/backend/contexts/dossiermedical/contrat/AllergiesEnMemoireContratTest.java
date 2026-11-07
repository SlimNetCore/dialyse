package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;

/** Le double en mémoire passe le contrat. */
class AllergiesEnMemoireContratTest extends AllergiesContrat {
    private final Allergies allergies = new AllergiesEnMemoire();

    @Override
    protected Allergies allergies() {
        return allergies;
    }
}
