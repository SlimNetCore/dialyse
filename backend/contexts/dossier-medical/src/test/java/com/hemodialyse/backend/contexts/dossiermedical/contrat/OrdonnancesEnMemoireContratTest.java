package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;

/** Le double en mémoire passe le contrat. */
class OrdonnancesEnMemoireContratTest extends OrdonnancesContrat {

    private final Ordonnances ordonnances = new OrdonnancesEnMemoire();

    @Override
    protected Ordonnances ordonnances() {
        return ordonnances;
    }
}
