package com.hemodialyse.backend.domain.reglement.specification;

import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.FactureReglementEtat;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.FactureSoldeType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FactureReglementStatusSpecification {

    private FactureReglementStatusSpecification() {
    }

    public static Evaluation evaluate(BigDecimal montantFacture, BigDecimal montantRegle) {
        BigDecimal totalFacture = normalize(montantFacture);
        BigDecimal totalRegle = normalize(montantRegle);
        BigDecimal delta = totalFacture.subtract(totalRegle).setScale(2, RoundingMode.HALF_UP);

        if (totalRegle.signum() == 0) {
            return new Evaluation(
                    FactureReglementEtat.NON_REGLEE,
                    FactureSoldeType.RESTE,
                    totalFacture,
                    BigDecimal.ZERO
            );
        }
        if (delta.signum() > 0) {
            return new Evaluation(
                    FactureReglementEtat.PARTIELLEMENT_REGLEE,
                    FactureSoldeType.RESTE,
                    delta,
                    BigDecimal.ZERO
            );
        }
        if (delta.signum() < 0) {
            return new Evaluation(
                    FactureReglementEtat.REGLEE,
                    FactureSoldeType.TROP_PERCU,
                    BigDecimal.ZERO,
                    delta.abs()
            );
        }
        return new Evaluation(
                FactureReglementEtat.REGLEE,
                FactureSoldeType.REGLE,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record Evaluation(
            FactureReglementEtat etat,
            FactureSoldeType soldeType,
            BigDecimal reste,
            BigDecimal tropPercu
    ) {
    }
}

