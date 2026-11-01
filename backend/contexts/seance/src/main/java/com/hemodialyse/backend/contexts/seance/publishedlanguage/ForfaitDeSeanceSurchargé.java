package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ForfaitDeSeanceSurchargé(UUID eventId, UUID seanceId, UUID centreId,
                                       UUID forfaitId, String code, String libelle,
                                       BigDecimal prixHt, BigDecimal montantSurcharge,
                                       String auteur, Instant instant) {
}