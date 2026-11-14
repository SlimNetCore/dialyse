package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port sortant : le forfait de la prise en charge du patient pour un jour (propriété de Patient et couverture ; LEC-PAT sous-slice ii,
 * ADR 0177). Effectif d'abord, sinon demandé, de la prise en charge dont la période couvre le jour ; à défaut la plus récente qui porte un
 * forfait. Le statut de la prise en charge n'est jamais regardé (KB-PAT-25, reproduit). Aucun : vide.
 */
public interface ForfaitDeLaPriseEnCharge {
    Optional<ForfaitsDeSeance.ForfaitCourant> duJour(CentreId centre, PatientId patient, LocalDate jour);
}
