package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : la fiche d'un patient du centre (Patient et couverture, hors contexte). Inconnu ou d'un autre centre : vide. */
public interface PatientsDeLaSeance {
    Optional<FicheDuPatient> fiche(CentreId centre, PatientId patient);
}
