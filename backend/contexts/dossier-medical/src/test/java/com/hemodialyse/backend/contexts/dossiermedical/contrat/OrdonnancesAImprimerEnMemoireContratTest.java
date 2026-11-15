package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Le double en mémoire passe le contrat. */
class OrdonnancesAImprimerEnMemoireContratTest extends OrdonnancesAImprimerContrat {

    private final OrdonnancesAImprimerEnMemoire memoire = new OrdonnancesAImprimerEnMemoire();

    @Override
    protected OrdonnancesAImprimer ordonnances() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, UUID id, UUID patient, String numero, LocalDate date, String medecin,
                         List<OrdonnancesAImprimer.LigneImprimable> lignes) {
        memoire.poser(centre, id, patient, numero, date, medecin, lignes);
    }
}
