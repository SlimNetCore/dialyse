package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.List;
import java.util.UUID;

public record ValidateSeanceRequest(
        UUID centerId,
        String userId,
        List<ValidateSeanceArticleRequest> consommations
) {
}


