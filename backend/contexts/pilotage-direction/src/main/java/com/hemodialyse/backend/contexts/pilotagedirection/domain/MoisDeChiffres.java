package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Le chiffre d'affaires d'un centre pour un mois civil (date de facturation). */
public record MoisDeChiffres(UUID centreId, int annee, int mois, BigDecimal caHt, BigDecimal caTtc) {
}
