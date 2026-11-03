package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : le verrou du centre, pris EN TÊTE de toute écriture de fiche (création et modification, avec ou sans place),
 * avant tout autre verrou et avant le contrôle d'unicité du numéro d'assurance. Ordre global des verrous (ADR 0061) : centre,
 * puis fiches, puis app_user. Tenu jusqu'à la fin de la transaction de l'unité de travail ; 409 si non obtenu dans le délai.
 */
public interface VerrouDuCentre {

    void prendre(CentreId centre);
}
