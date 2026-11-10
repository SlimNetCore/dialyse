package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;

import java.util.UUID;

/**
 * Ce que les séances retiennent d'un forfait du catalogue (RF2, ADR 0124) : le nombre de séances qui l'imposent (langage publié de la
 * Séance, SE7e, ADR 0099 ; même comptage qu'avant, sans centre ni statut, KB-SEA-91). Additionné aux autres usages par le catalogue
 * pour refuser la suppression d'un forfait utilisé.
 */
public class UsageDuForfaitParLesSeances implements UsageDuForfait {

    private final ForfaitsImposesAuxSeancesPublies imposes;

    public UsageDuForfaitParLesSeances(ForfaitsImposesAuxSeancesPublies imposes) {
        this.imposes = imposes;
    }

    @Override
    public long nombreDeReferences(UUID forfait) {
        return imposes.nombreDeSeancesQuiImposentLeForfait(forfait);
    }
}
