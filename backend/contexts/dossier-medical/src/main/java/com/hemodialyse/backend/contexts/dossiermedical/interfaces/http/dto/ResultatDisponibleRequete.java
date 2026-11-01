package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.Instant;
import java.util.UUID;

public record ResultatDisponibleRequete(UUID centreId, UUID eventId, Instant maintenant) {
}
