package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

/**
 * Service de domaine R7 : un code d'équipement est unique dans son centre. La règle porte sur l'ensemble des équipements,
 * elle n'appartient à aucun agrégat. Comparaison exacte, casse et espaces compris (KB-GMAO-33).
 */
public final class UniciteDuCodeEquipement {

    private final CodesEquipementDuCentre codes;

    public UniciteDuCodeEquipement(CodesEquipementDuCentre codes) {
        this.codes = codes;
    }

    public void verifier(CentreId centre, CodeEquipement code) {
        if (codes.estUtilise(centre, code)) throw new CodeEquipementDejaUtilise();
    }
}
