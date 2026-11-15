package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Le double en mémoire passe le contrat. */
class ListeDesAttestationsAImprimerEnMemoireContratTest extends ListeDesAttestationsAImprimerContrat {

    private final ListeDesAttestationsAImprimerEnMemoire memoire = new ListeDesAttestationsAImprimerEnMemoire();

    @Override
    protected ListeDesAttestationsAImprimer liste() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, ListeDesAttestationsAImprimer.AttestationImprimee attestation) {
        memoire.poser(centre, attestation);
    }

    @Override
    protected void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient,
                                              ListeDesAttestationsAImprimer.AttestationImprimee attestation) {
        memoire.poserPourUnPatientDuCentre(centre, centreDuPatient, attestation);
    }
}
