package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.EvenementIntervention;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrée de la ligne de temps : événement, instant UTC, auteur (identifiant et nom).
 */
public record EvenementInterventionResponse(
        EvenementIntervention.Type type,
        OffsetDateTime at,
        UUID parId,
        String parNom
) {
}
