package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import java.math.BigDecimal;

/**
 * Un remboursement ne peut pas dépasser le total déjà réglé de la facture (KB-FAC-42/43, décision du 2026-10-21) : refus 422.
 */
public class RemboursementSuperieurAuTotalRegle extends RuntimeException {

    private final BigDecimal rembourse;
    private final BigDecimal dejaRegle;

    public RemboursementSuperieurAuTotalRegle(BigDecimal rembourse, BigDecimal dejaRegle) {
        super("Le remboursement de " + rembourse.toPlainString() + " depasse le total deja regle de la facture ("
                + dejaRegle.max(BigDecimal.ZERO).setScale(2).toPlainString() + ")");
        this.rembourse = rembourse;
        this.dejaRegle = dejaRegle;
    }

    public BigDecimal rembourse() {
        return rembourse;
    }

    public BigDecimal dejaRegle() {
        return dejaRegle;
    }
}
