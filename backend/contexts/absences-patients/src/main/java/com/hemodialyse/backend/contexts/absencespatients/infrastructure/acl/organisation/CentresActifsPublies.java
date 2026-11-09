package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.absencespatients.application.CentresActifs;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : les centres actifs, par identifiant. Un centre dont
 * l'état n'a jamais été renseigné est actif ; seul un centre désactivé est exclu. Remplace {@code CentresActifsJdbc} (SQL direct).
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
