package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;

/** Le double en mémoire passe le contrat. */
class ObservationsBiologiquesEnMemoireContratTest extends ObservationsBiologiquesContrat {
    private final ObservationsBiologiques observations = new ObservationsBiologiquesEnMemoire();

    @Override
    protected ObservationsBiologiques observations() {
        return observations;
    }
}
