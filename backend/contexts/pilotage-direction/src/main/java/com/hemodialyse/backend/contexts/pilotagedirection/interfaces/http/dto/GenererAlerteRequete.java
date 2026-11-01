package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record GenererAlerteRequete(UUID centreId, String type, String seuil,
                                   String message, LocalDate date) {
}
