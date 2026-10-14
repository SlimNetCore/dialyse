package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.doubles.IntervenantsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;

/** Le double in-memory passe le contrat partagé des intervenants. */
class IntervenantEnMemoireContratTest extends IntervenantContrat {

    private final IntervenantsEnMemoire memoire = new IntervenantsEnMemoire();

    @Override
    protected IntervenantRepository repository() {
        return memoire;
    }

    @Override
    protected LectureIntervenants lecture() {
        return memoire;
    }
}
