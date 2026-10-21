package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.doubles.MoisDeFacturationEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;

/**
 * Le double en mémoire passe le contrat partagé des mois de facturation.
 */
class MoisDeFacturationEnMemoireContratTest extends MoisDeFacturationContrat {

    private final MoisDeFacturationEnMemoire memoire = new MoisDeFacturationEnMemoire();

    @Override
    protected MoisDeFacturationRepository mois() {
        return memoire;
    }
}
