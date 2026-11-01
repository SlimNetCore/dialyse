package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto;

import java.util.UUID;

public record GenererExportRequete(UUID centreId, String periode, String format) {
}
