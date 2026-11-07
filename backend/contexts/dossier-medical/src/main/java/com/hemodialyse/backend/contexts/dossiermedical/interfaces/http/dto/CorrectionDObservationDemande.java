package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Corps de la correction d'une observation : seule la valeur change (les autres champs envoyés sont ignorés, reproduit). */
public record CorrectionDObservationDemande(UUID centerId, BigDecimal valeurNum, String unite, String valeurTexte) {
}
