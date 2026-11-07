package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de la résolution d'un antécédent. */
public record ResolutionDAntecedentDemande(UUID centerId, LocalDate dateResolution) {
}
