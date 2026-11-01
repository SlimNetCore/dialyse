package com.hemodialyse.backend.contexts.stock.interfaces.http.dto;

import java.util.UUID;

/**
 * Accusé de démarrage d'un recalcul massif du PMP.
 */
public record Demarrage(UUID jobId) {
}
