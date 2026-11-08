package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.util.UUID;

/** Corps de {@code POST /api/v1/seances/{id}/signer-medecin} : {@code userId} est le signataire (KB-SEA-11, reproduit). */
public record SignatureDeSeanceDemande(UUID centerId, String userId) {
}
