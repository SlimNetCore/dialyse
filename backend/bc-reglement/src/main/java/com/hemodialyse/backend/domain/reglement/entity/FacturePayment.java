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
        String saisiPar
) {
    public FacturePayment {
        if (id == null || factureId == null || centerId == null) {
            throw new IllegalArgumentException("Les identifiants du reglement sont obligatoires");
        }
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant du reglement doit etre strictement positif");
        }
        if (dateReglement == null) {
            throw new IllegalArgumentException("La date du reglement est obligatoire");
        }
        saisiPar = saisiPar != null && !saisiPar.isBlank() ? saisiPar.trim() : "system";
    }
}

