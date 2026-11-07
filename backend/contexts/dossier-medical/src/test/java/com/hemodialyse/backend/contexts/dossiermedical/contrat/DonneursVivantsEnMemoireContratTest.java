package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;

/** Le double en mémoire passe le contrat. */
class DonneursVivantsEnMemoireContratTest extends DonneursVivantsContrat {
    private final DonneursVivants donneurs = new DonneursVivantsEnMemoire();

    @Override
    protected DonneursVivants donneurs() {
        return donneurs;
    }
}
