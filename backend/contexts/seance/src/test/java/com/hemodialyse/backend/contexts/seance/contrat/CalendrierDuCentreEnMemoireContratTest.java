package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;

/** Le double en mémoire du calendrier du centre passe le contrat. */
class CalendrierDuCentreEnMemoireContratTest extends CalendrierDuCentreContrat {
    @Override
    protected CalendrierDuCentre calendrier() {
        return new CalendrierDuCentreEnMemoire();
    }
}
