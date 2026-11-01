package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto;

import java.util.UUID;

public record CapturerInstantaneRequete(UUID centreId, int annee, int mois, String donnees) {
}
