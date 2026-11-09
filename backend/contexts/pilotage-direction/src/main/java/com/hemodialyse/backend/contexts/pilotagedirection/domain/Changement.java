package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Un indicateur d'un centre dont la valeur a changé entre deux constats (ou une alerte apparue ou disparue : l'autre valeur est zéro). */
public record Changement(UUID centreId, String centre, String famille, String nom, BigDecimal avant, BigDecimal apres) {
}
