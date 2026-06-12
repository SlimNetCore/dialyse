package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record ValidateSeanceArticleRequest(
        UUID articleId,
        BigDecimal quantite
) {
}

