package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;

/** Le double en mémoire passe le contrat. */
class AdministrationsEnMemoireContratTest extends AdministrationsContrat {

    private final Administrations administrations = new AdministrationsEnMemoire();

    @Override
    protected Administrations administrations() {
        return administrations;
    }
}
