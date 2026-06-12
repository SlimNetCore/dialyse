package com.hemodialyse.backend.domain.seance.model;

import java.math.BigDecimal;
import java.util.UUID;

public record SeanceArticleConsumption(UUID articleId, BigDecimal quantite) {
}

