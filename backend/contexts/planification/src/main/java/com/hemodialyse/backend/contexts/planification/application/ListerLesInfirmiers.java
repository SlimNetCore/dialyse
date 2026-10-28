package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Liste le référentiel des infirmiers du centre (actifs et inactifs) par nom, prénom puis matricule, avec le roulement et le
 * compte de chaque fiche. Lecture : ni verrou ni transaction d'écriture.
 */
public class ListerLesInfirmiers {
    private final InfirmiersRepository infirmiers;
    private final FichesDetaillees fiches;

    public ListerLesInfirmiers(InfirmiersRepository infirmiers, FichesDetaillees fiches) {
        this.infirmiers = infirmiers;
        this.fiches = fiches;
    }

    public PageDeResultats<FicheDetaillee> executer(CentreId centre, Pagination pagination) {
        PageDeResultats<Infirmier> page = infirmiers.page(centre, pagination);
        return new PageDeResultats<>(fiches.de(page.elements()), page.total(), page.page(), page.taille());
    }
}
