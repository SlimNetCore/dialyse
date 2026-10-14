package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.doubles.EquipementsEnMemoire;

class EquipementExisteEnMemoireContratTest extends EquipementExisteContrat {

    private final EquipementsEnMemoire memoire = new EquipementsEnMemoire();

    @Override
    protected EquipementExiste equipements() {
        return memoire;
    }

    @Override
    protected void preparerLeReferentiel() {
        memoire.avec(CONNU, SUPPRIME_LOGIQUEMENT);
    }
}
