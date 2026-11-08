package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;

/** Le contrat des raccourcis rejoué sur le double en mémoire. */
class RaccourcisEnMemoireContratTest extends RaccourcisContrat {

    private final RaccourcisDesCentresEnMemoire memoire = new RaccourcisDesCentresEnMemoire();

    @Override
    protected RaccourcisDesCentres depot() {
        return memoire;
    }
}
