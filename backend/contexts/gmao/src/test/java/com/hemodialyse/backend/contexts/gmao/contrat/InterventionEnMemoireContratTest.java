package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;

/** Le double in-memory passe le contrat partagé des interventions. */
class InterventionEnMemoireContratTest extends InterventionContrat {

    private final InterventionsEnMemoire memoire = new InterventionsEnMemoire();

    @Override
    protected InterventionRepository repository() {
        return memoire;
    }

    @Override
    protected LectureInterventions lecture() {
        return memoire;
    }

    @Override
    protected void supprimerLogiquement(InterventionId id) {
        memoire.supprimerLogiquement(id);
    }
}
