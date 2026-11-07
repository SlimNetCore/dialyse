package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Le contrat du port rejoué sur le double en mémoire. */
class SeancesEnMemoireContratTest extends SeancesContrat {

    @Override
    protected Banc nouveauBanc() {
        var memoire = new SeancesEnMemoire();
        return new Banc() {
            @Override
            public Seances seances() {
                return memoire;
            }

            @Override
            public void semer(Seance seance) {
                memoire.semer(seance);
            }

            @Override
            public void semerUneLigneIllisible(CentreId centre, SeanceId id) {
                memoire.semerUneLigneIllisible(centre, id);
            }
        };
    }
}
