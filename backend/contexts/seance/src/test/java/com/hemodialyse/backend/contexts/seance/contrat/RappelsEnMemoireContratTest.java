package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;

import java.util.List;

/** Contrat des rappels sur le double en mémoire. */
class RappelsEnMemoireContratTest extends RappelsContrat {
    private final RappelsEnMemoire enMemoire = new RappelsEnMemoire();

    @Override
    protected RappelsDeRegularisation rappels() {
        return enMemoire;
    }

    @Override
    protected List<String> emissions() {
        return enMemoire.emissions();
    }
}
