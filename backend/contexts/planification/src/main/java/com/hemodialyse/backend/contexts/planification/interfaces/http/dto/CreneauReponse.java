package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import java.util.UUID;

public record CreneauReponse(UUID id, String libelle, int ordre) {
}
