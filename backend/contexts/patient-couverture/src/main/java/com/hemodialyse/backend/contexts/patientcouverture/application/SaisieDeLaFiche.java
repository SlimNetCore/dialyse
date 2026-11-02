package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;

import java.util.UUID;

/**
 * Saisie de la fiche (création ou modification) : le centre demandé (celui du corps, comparé à la session), la fiche, et ce que
 * la couverture enregistre avec elle.
 */
public record SaisieDeLaFiche(UUID centreDemande, Fiche fiche, CouvertureDeLaFiche.AttestationSaisie attestation,
                              CouvertureDeLaFiche.DemandeDePec demande) {
}
