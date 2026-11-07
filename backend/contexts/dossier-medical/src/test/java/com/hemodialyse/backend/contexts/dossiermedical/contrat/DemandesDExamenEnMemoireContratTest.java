package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;

/** Le double en mémoire passe le contrat. */
class DemandesDExamenEnMemoireContratTest extends DemandesDExamenContrat {

    private final DemandesDExamen demandes = new DemandesDExamenEnMemoire();

    @Override
    protected DemandesDExamen demandes() {
        return demandes;
    }
}
