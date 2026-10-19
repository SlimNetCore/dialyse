package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.doubles.ParametresEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;

/**
 * Le double en mémoire passe le contrat partagé des paramètres de facturation.
 */
class ParametresEnMemoireContratTest extends ParametresDeFacturationContrat {

    private final ParametresEnMemoire memoire = new ParametresEnMemoire();

    @Override
    protected ParametresDeFacturationRepository repository() {
        return memoire;
    }
}
