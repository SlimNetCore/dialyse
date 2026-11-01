package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.util.UUID;

public record CreerDerogationRequest(UUID seanceId, UUID centreId, String motif, String autorite) {
}
