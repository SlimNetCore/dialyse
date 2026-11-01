package com.hemodialyse.backend.contexts.stock.interfaces.http.dto;

import java.util.List;
import java.util.UUID;

/**
 * Demande de recalcul massif du PMP : centre de la session et articles ciblés.
 */
public record Demande(UUID centerId, List<UUID> articleIds) {
}
