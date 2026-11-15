package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Le double en mémoire passe le contrat. */
class PriseEnChargeAImprimerEnMemoireContratTest extends PriseEnChargeAImprimerContrat {

    private final PriseEnChargeAImprimerEnMemoire memoire = new PriseEnChargeAImprimerEnMemoire();

    @Override
    protected PriseEnChargeAImprimer prisesEnCharge() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID pec,
                         PriseEnChargeAImprimer.PriseEnChargeImprimable p) {
        memoire.poser(centre, centreDuPatient, patient, pec, p);
    }
}
