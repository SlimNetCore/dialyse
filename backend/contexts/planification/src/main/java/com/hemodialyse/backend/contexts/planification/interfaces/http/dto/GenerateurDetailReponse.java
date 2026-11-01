package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import java.util.UUID;

public record GenerateurDetailReponse(UUID id, String code, String designation, String statut) {
}
