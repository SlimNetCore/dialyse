package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Le double en mémoire passe le contrat. */
class AttestationAImprimerEnMemoireContratTest extends AttestationAImprimerContrat {

    private final AttestationAImprimerEnMemoire memoire = new AttestationAImprimerEnMemoire();

    @Override
    protected AttestationAImprimer attestations() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID attestation,
                         AttestationAImprimer.AttestationImprimable a) {
        memoire.poser(centre, centreDuPatient, patient, attestation, a);
    }
}
