package com.hemodialyse.backend.infrastructure.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un point de la courbe d'évolution (Hb, ferritine...) affichée dans le suivi de l'anémie.
 */
public record PointBiologiqueResponse(
        LocalDate date,
        BigDecimal hbGDl,
        BigDecimal ferritineNgMl,
        BigDecimal cstfPct,
        BigDecimal albumineGDl
) {
}
