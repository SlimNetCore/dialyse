package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;

/** Le double en mémoire des volets paramédicaux passe le contrat. */
class VoletsParamedicauxEnMemoireContratTest extends VoletsParamedicauxContrat {
    @Override
    protected VoletsParamedicaux volets() {
        return new VoletsParamedicauxEnMemoire();
    }

    @Override
    protected Class<? extends RuntimeException> violationDUnicite() {
        return VoletsParamedicauxEnMemoire.UniciteParSeanceViolee.class;
    }
}
