package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : le nom d'un centre (tenu par l'Organisation, hors contexte), imprimé en titre de l'ordonnance (DOC5d, ADR 0196). */
public interface NomsDesCentres {

    /** Le nom du centre ; centre inconnu : vide. */
    Optional<String> duCentre(CentreId centre);
}
