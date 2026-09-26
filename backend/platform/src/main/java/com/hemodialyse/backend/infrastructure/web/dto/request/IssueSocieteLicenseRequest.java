package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Attribution d'une licence à une société : une licence est émise pour chacun de ses centres actifs, ou pour la
 * seule sélection {@code centerIds}.
 */
public record IssueSocieteLicenseRequest(UUID societeId, List<UUID> centerIds, String type, int maxUsers,
                                         Instant validFrom, Instant validUntil) {
}
