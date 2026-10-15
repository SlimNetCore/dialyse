package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.plan.LecturePlan;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;

/**
 * Le double en mémoire passe le contrat partagé du plan comptable.
 */
class CompteEnMemoireContratTest extends CompteContrat {

    private final ComptesEnMemoire memoire = new ComptesEnMemoire();

    @Override
    protected CompteRepository repository() {
        return memoire;
    }

    @Override
    protected LecturePlan lecture() {
        return memoire;
    }
}
