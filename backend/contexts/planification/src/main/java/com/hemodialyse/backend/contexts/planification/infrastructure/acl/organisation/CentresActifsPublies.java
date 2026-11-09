package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : les centres actifs, par identifiant. Même définition
 * que les Absences : un centre dont l'état n'a jamais été renseigné est actif (le legacy libérait tous les centres) ; seul un centre
 * désactivé est exclu (revue P7, « centre actif divergent »). Remplace {@code CentresActifsLegacy}.
 */
public class CentresActifsPublies implements CentresActifs {

    private final AnnuaireDesCentres centres;

    public CentresActifsPublies(AnnuaireDesCentres centres) {
        this.centres = centres;
    }

    @Override
    public List<CentreId> liste() {
        return centres.actifs().stream().map(CentreId::new).toList();
    }
}
