package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.IntervenantsDuCentre;
import com.hemodialyse.backend.contexts.gmao.doubles.IntervenantsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;

/** Le double in-memory passe le contrat du port des intervenants du centre. */
class IntervenantsDuCentreEnMemoireContratTest extends IntervenantsDuCentreContrat {

    private final IntervenantsEnMemoire memoire = new IntervenantsEnMemoire();

    @Override
    protected IntervenantRepository intervenants() {
        return memoire;
    }

    @Override
    protected IntervenantsDuCentre port() {
        return memoire;
    }
}
