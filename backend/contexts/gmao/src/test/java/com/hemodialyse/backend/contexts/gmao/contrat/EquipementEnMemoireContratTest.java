package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.doubles.ParcEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodesEquipementDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;

/** Le double in-memory passe le contrat partagé des équipements. */
class EquipementEnMemoireContratTest extends EquipementContrat {

    private final ParcEnMemoire memoire = new ParcEnMemoire();

    @Override
    protected EquipementRepository repository() {
        return memoire;
    }

    @Override
    protected CodesEquipementDuCentre codes() {
        return memoire;
    }

    @Override
    protected LectureEquipements lecture() {
        return memoire;
    }

    @Override
    protected void supprimerLogiquement(EquipementId id) {
        memoire.supprimerLogiquement(id);
    }
}
