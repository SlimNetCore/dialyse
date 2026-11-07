package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

/** Corps de la correction d'une allergie : seuls la criticité, les manifestations et le statut se corrigent. */
public record CorrectionDAllergieDemande(UUID centerId, String criticite, String manifestations, String statutVerification) {
}
