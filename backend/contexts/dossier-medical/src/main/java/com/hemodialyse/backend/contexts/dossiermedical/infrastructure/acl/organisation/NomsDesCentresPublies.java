package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.dossiermedical.application.NomsDesCentres;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** ACL vers le langage publié d'Organisation (organisation.v1, ADR 0174) : le nom du centre imprimé en titre de l'ordonnance (DOC5d, ADR 0196). */
public class NomsDesCentresPublies implements NomsDesCentres {

    private final AnnuaireDesCentres centres;

    public NomsDesCentresPublies(AnnuaireDesCentres centres) {
        this.centres = centres;
    }

    @Override
    public Optional<String> duCentre(CentreId centre) {
        return centres.nomDuCentre(centre.valeur());
    }
}
