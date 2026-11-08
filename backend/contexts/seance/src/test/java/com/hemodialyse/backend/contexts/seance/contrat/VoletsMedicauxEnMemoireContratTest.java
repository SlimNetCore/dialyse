package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;

/** Le double en mémoire des volets médicaux passe le contrat. */
class VoletsMedicauxEnMemoireContratTest extends VoletsMedicauxContrat {
    @Override
    protected VoletsMedicaux volets() {
        return new VoletsMedicauxEnMemoire();
    }
}
