package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.SaisiesDeLaSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Effacement des saisies d'une séance supprimée (SE6), dans l'ordre du legacy : administrations de traitement (Dossier médical, encore
 * fournies par la plateforme : {@link SaisiesDeSeanceFournies}, le Dossier médical dépendant de la Séance), puis volet paramédical, puis
 * volet médical (dépôts de la Séance depuis SE7f, ADR 0100). Aucune transaction propre : l'unité de travail de la suppression.
 */
public class SaisiesParLeLegacy implements SaisiesDeLaSeance {

    private final SaisiesDeSeanceFournies administrations;
    private final VoletsParamedicaux voletsParamedicaux;
    private final VoletsMedicaux voletsMedicaux;

    public SaisiesParLeLegacy(SaisiesDeSeanceFournies administrations, VoletsParamedicaux voletsParamedicaux, VoletsMedicaux voletsMedicaux) {
        this.administrations = administrations;
        this.voletsParamedicaux = voletsParamedicaux;
        this.voletsMedicaux = voletsMedicaux;
    }

    @Override
    public void effacer(CentreId centre, SeanceId seance) {
        administrations.effacerLesAdministrations(centre.valeur(), seance.valeur());
        voletsParamedicaux.effacer(centre, seance);
        voletsMedicaux.effacer(centre, seance);
    }
}
