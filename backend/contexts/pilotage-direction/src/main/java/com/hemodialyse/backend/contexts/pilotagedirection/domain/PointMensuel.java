package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** L'activité d'un centre pour un mois civil ({@code mois} : {@code AAAA-MM}). */
public record PointMensuel(String mois, UUID centreId, long seances, BigDecimal caHt, BigDecimal caTtc) {
}
