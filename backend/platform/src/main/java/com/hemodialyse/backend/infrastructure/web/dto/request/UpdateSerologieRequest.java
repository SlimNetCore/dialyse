package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.UUID;

public record UpdateSerologieRequest(
        UUID centerId,
        String resultat,
        String conduiteATenir
) {
}
