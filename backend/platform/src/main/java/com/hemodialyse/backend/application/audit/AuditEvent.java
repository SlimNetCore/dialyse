package com.hemodialyse.backend.application.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un évènement de traçabilité : qui a fait quoi, quand, sur quel centre/société, avec quel résultat.
 * Immuable, sans dépendance Spring ni JPA — construit par {@link AuditRequestFilter} puis mis en file
 * d'attente pour écriture asynchrone par {@link AuditWriterService}.
 */
public record AuditEvent(
        OffsetDateTime occurredAt,
        UUID userId,
        String username,
        String roles,
        UUID centerId,
        UUID societeId,
        String actionCode,
        String entityType,
        String entityId,
        String libelle,
        String httpMethod,
        String routeTemplate,
        int statusCode,
        long durationMs,
        String ipAddress
) {
}
