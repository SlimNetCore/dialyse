package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;

/** Le double en mémoire passe le contrat. */
class DossiersMedicauxDeBaseEnMemoireContratTest extends DossiersMedicauxDeBaseContrat {
    private final DossiersMedicauxDeBase dossiers = new DossiersMedicauxDeBaseEnMemoire();

    @Override
    protected DossiersMedicauxDeBase dossiers() {
        return dossiers;
    }
}
