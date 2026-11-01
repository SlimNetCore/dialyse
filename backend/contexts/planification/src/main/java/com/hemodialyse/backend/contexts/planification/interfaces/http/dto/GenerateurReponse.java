package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import java.util.UUID;

public record GenerateurReponse(UUID id, String code, UUID salleId) {
}
