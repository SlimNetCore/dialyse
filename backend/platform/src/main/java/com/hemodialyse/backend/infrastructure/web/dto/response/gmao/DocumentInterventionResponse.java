package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.DocumentIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeDocumentIntervention;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Métadonnées d'une pièce jointe (le contenu se télécharge via l'endpoint dédié).
 */
public record DocumentInterventionResponse(
        UUID id,
        UUID interventionId,
        TypeDocumentIntervention type,
        String nom,
        String contentType,
        long taille,
        OffsetDateTime ajouteLe
) {
    public DocumentInterventionResponse(DocumentIntervention d) {
        this(d.id(), d.interventionId(), d.type(), d.nom(), d.contentType(), d.taille(), d.ajouteLe());
    }
}
