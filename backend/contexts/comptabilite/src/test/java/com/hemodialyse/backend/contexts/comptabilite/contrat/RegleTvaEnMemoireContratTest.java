package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.doubles.ReglesTvaEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

/**
 * Le double en mémoire passe le contrat partagé des règles de TVA.
 */
class RegleTvaEnMemoireContratTest extends RegleTvaContrat {

    private final ReglesTvaEnMemoire memoire = new ReglesTvaEnMemoire();

    @Override
    protected RegleTvaRepository repository() {
        return memoire;
    }

    @Override
    protected void regleDesactivee(CentreId centre, String type, String debut) {
        memoire.enBase(ReglesTvaEnMemoire.regle(centre, type, "7.00", false, jour(debut), null, null, false));
    }
}
