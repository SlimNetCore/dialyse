package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Précise le générateur d'une fiche par l'équipement de la GMAO, seule source de vérité : sans équipement connu, la fiche reste telle quelle. */
final class Generateurs {
    private Generateurs() {
    }

    static FicheDuPatient preciser(GenerateursDesPatients generateurs, CentreId centre, FicheDuPatient fiche) {
        if (fiche.generateur() == null) return fiche;
        return generateurs.duCentre(centre, fiche.generateur()).map(fiche::avecLeGenerateur).orElse(fiche);
    }
}
