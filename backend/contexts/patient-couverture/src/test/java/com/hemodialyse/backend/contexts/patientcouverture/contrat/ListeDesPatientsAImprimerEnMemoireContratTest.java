package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Le double en mémoire passe le contrat. */
class ListeDesPatientsAImprimerEnMemoireContratTest extends ListeDesPatientsAImprimerContrat {

    private final ListeDesPatientsAImprimerEnMemoire memoire = new ListeDesPatientsAImprimerEnMemoire();

    @Override
    protected ListeDesPatientsAImprimer liste() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, ListeDesPatientsAImprimer.PatientImprime patient) {
        memoire.poser(centre, patient);
    }
}
