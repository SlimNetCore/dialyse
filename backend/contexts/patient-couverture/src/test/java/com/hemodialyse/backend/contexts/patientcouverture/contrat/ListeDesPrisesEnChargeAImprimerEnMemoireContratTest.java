package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Le double en mémoire passe le contrat. */
class ListeDesPrisesEnChargeAImprimerEnMemoireContratTest extends ListeDesPrisesEnChargeAImprimerContrat {

    private final ListeDesPrisesEnChargeAImprimerEnMemoire memoire = new ListeDesPrisesEnChargeAImprimerEnMemoire();

    @Override
    protected ListeDesPrisesEnChargeAImprimer liste() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, ListeDesPrisesEnChargeAImprimer.PriseEnChargeImprimee priseEnCharge) {
        memoire.poser(centre, priseEnCharge);
    }

    @Override
    protected void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient,
                                              ListeDesPrisesEnChargeAImprimer.PriseEnChargeImprimee priseEnCharge) {
        memoire.poserPourUnPatientDuCentre(centre, centreDuPatient, priseEnCharge);
    }
}
