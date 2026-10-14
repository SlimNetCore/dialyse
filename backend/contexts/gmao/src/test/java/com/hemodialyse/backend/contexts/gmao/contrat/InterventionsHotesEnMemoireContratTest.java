package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsHotesEnMemoire;

/** Le double in-memory passe le contrat partagé des interventions hôtes. */
class InterventionsHotesEnMemoireContratTest extends InterventionsHotesContrat {

    private final InterventionsHotesEnMemoire memoire = new InterventionsHotesEnMemoire();

    @Override
    protected InterventionsHotes hotes() {
        return memoire;
    }

    @Override
    protected void semer(InterventionHote hote) {
        memoire.ajouter(hote);
    }
}
