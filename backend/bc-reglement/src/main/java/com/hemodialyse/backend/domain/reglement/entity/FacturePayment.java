package com.hemodialyse.backend.domain.reglement.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FacturePayment(
        UUID id,
        UUID factureId,
        UUID centerId,
        BigDecimal montant,
        LocalDate dateReglement,
        String saisiPar,
        String codeReglement
) {
    public FacturePayment {
        if (id == null || factureId == null || centerId == null) {
            throw new IllegalArgumentException("Les identifiants du reglement sont obligatoires");
        }
        if (montant == null || montant.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Le montant du reglement ne peut pas etre nul");
        }
        if (dateReglement == null) {
            throw new IllegalArgumentException("La date du reglement est obligatoire");
        }
        saisiPar = saisiPar != null && !saisiPar.isBlank() ? saisiPar.trim() : "system";
        if (codeReglement == null || codeReglement.isBlank()) {
            codeReglement = "REG-" + id.toString().substring(0, 8).toUpperCase();
        }
    }
}
