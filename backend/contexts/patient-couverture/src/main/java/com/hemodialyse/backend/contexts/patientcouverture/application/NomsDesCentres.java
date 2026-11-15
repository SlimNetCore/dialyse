package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : le nom d'un centre (tenu par l'Organisation, hors contexte), imprimé en titre de l'attestation et de la prise en charge (DOC5h, ADR 0200). */
public interface NomsDesCentres {

    /** Le nom du centre ; centre inconnu : vide. */
    Optional<String> duCentre(CentreId centre);
}
