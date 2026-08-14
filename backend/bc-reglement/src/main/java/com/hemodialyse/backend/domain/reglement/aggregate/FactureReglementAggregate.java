package com.hemodialyse.backend.domain.reglement.aggregate;

import com.hemodialyse.backend.domain.reglement.entity.FacturePayment;
import com.hemodialyse.backend.domain.reglement.specification.FactureReglementStatusSpecification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FactureReglementAggregate(UUID factureId, UUID centerId, BigDecimal montantFacture,
                                        List<FacturePayment> paiements) {
    public FactureReglementAggregate(UUID factureId, UUID centerId, BigDecimal montantFacture, List<FacturePayment> paiements) {
        if (factureId == null || centerId == null) {
            throw new IllegalArgumentException("Les identifiants facture et centre sont obligatoires");
        }
        if (montantFacture == null || montantFacture.signum() < 0) {
            throw new IllegalArgumentException("Le montant facture doit etre positif");
        }
        this.factureId = factureId;
        this.centerId = centerId;
        this.montantFacture = montantFacture.setScale(2, RoundingMode.HALF_UP);
        this.paiements = List.copyOf(paiements != null ? paiements : new ArrayList<>());
    }

    public FacturePayment enregistrerPaiement(BigDecimal montant, LocalDate dateReglement, String userId) {
        return new FacturePayment(
                UUID.randomUUID(),
                factureId,
                centerId,
                montant.setScale(2, RoundingMode.HALF_UP),
                dateReglement,
                userId
        );
    }

    public BigDecimal montantRegle() {
        return paiements.stream()
                .map(FacturePayment::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public FactureReglementStatusSpecification.Evaluation situation() {
        return FactureReglementStatusSpecification.evaluate(montantFacture, montantRegle());
    }
}

