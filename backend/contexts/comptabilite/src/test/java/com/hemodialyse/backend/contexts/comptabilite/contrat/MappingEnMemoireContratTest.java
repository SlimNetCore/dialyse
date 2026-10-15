package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.doubles.MappingsEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;

/**
 * Le double en mémoire passe le contrat partagé du mapping.
 */
class MappingEnMemoireContratTest extends MappingContrat {

    private final MappingsEnMemoire memoire = new MappingsEnMemoire();

    @Override
    protected MappingComptableRepository repository() {
        return memoire;
    }

    @Override
    protected void ligneHeritee(CentreId centre) {
        memoire.enregistrer(MappingComptable.reconstitue(centre, "7060", "411100", "411500", "512", "530", null, null, null));
    }
}
