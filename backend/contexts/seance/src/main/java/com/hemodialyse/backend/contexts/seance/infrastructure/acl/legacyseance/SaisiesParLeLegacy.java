package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.SaisiesDeLaSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL des saisies de la séance : traduit {@link SaisiesDeSeanceFournies}. L'ordre du legacy est conservé (administrations, volet
 * paramédical, volet médical).
 */
public class SaisiesParLeLegacy implements SaisiesDeLaSeance {

    private final SaisiesDeSeanceFournies saisies;

    public SaisiesParLeLegacy(SaisiesDeSeanceFournies saisies) {
        this.saisies = saisies;
    }

    @Override
    public void effacer(CentreId centre, SeanceId seance) {
        saisies.effacerLesAdministrations(centre.valeur(), seance.valeur());
        saisies.effacerLeVoletParamedical(centre.valeur(), seance.valeur());
        saisies.effacerLeVoletMedical(centre.valeur(), seance.valeur());
    }
}
