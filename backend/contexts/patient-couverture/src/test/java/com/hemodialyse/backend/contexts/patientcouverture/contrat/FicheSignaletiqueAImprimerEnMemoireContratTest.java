package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Le double en mémoire passe le contrat. */
class FicheSignaletiqueAImprimerEnMemoireContratTest extends FicheSignaletiqueAImprimerContrat {

    private final FicheSignaletiqueAImprimerEnMemoire memoire = new FicheSignaletiqueAImprimerEnMemoire();

    @Override
    protected FicheSignaletiqueAImprimer fiches() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, UUID patient, FicheSignaletiqueAImprimer.FicheImprimable fiche) {
        memoire.poser(centre, patient, fiche);
    }
}
