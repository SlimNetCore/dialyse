package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.doubles.SequenceEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;

/**
 * Le double en mémoire passe le contrat partagé de la séquence.
 */
class SequenceEnMemoireContratTest extends SequenceContrat {

    private final SequenceEnMemoire memoire = new SequenceEnMemoire();

    @Override
    protected SequenceDeFacturation sequence() {
        return memoire;
    }

    @Override
    protected void poser(CentreId centre, int annee, int valeur) {
        memoire.poser(centre, annee, valeur);
    }
}
