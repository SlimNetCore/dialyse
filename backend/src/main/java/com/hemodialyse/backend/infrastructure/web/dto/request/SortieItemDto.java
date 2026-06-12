package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record SortieItemDto(
        UUID articleId,
        BigDecimal quantite
) {
}

