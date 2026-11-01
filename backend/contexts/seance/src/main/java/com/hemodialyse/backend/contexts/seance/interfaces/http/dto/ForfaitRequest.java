package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ForfaitRequest(UUID centreId, UUID forfaitId, String code, String libelle,
                             BigDecimal prixHt, BigDecimal montantSurcharge, String auteur) {
}
