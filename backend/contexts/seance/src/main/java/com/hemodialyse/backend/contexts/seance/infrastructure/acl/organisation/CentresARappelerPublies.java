package com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.seance.application.CentresARappeler;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : tous les centres, sans filtre d'activité, comme le
 * lecteur SQL qu'elle remplace (ancien {@code CentresARappelerJdbc}, liste des identifiants de la table des centres).
 */
public class CentresARappelerPublies implements CentresARappeler {

    private final AnnuaireDesCentres centres;

    public CentresARappelerPublies(AnnuaireDesCentres centres) {
        this.centres = centres;
    }

    @Override
    public List<CentreId> tous() {
        return centres.tous().stream().map(CentreId::new).toList();
    }
}
