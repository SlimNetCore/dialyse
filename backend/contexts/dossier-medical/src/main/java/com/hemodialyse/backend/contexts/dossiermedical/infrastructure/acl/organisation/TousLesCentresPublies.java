package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.dossiermedical.application.CentresActifs;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : TOUS les centres, sans filtre d'activité, comme le
 * lecteur SQL qu'elle remplace (la liste des identifiants de la table des centres, ancien {@code CentresActifsJdbc}). Le port garde son nom historique
 * {@code CentresActifs} ; son comportement (tous les centres) est inchangé (parité, ADR 0107).
 */
public class TousLesCentresPublies implements CentresActifs {

    private final AnnuaireDesCentres centres;

    public TousLesCentresPublies(AnnuaireDesCentres centres) {
        this.centres = centres;
    }

    @Override
    public List<CentreId> tous() {
        return centres.tous().stream().map(CentreId::new).toList();
    }
}
