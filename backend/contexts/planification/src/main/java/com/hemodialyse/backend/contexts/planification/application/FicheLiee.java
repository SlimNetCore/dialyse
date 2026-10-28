package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;

/**
 * La fiche du centre liée au compte connecté, sinon 422 {@code INFIRMIER_NON_LIE} (« Mon planning »).
 */
final class FicheLiee {
    private FicheLiee() {
    }

    static Infirmier de(InfirmiersRepository infirmiers, CentreId centre, CompteId compte) {
        return infirmiers.parCompte(centre, compte).orElseThrow(FicheLiee::nonLie);
    }

    static RegleDePlanificationViolee nonLie() {
        return new RegleDePlanificationViolee("INFIRMIER_NON_LIE", "Votre compte n'est relié à aucune fiche infirmier de ce centre");
    }
}
