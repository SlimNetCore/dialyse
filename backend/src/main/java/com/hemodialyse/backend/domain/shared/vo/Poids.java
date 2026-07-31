package com.hemodialyse.backend.domain.shared.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;

/**
 * Value Object — a body weight expressed in kilograms (Shared Kernel, AGENTS.md §16).
 * <p>
 * Immutable; removes the "primitive obsession" of raw {@link BigDecimal} weights
 * on the dialysis sheets. Bounded to a plausible human range to catch input errors.
 */
public final class Poids {

    private static final BigDecimal MAX_KG = new BigDecimal("500");

    private final BigDecimal kilogrammes;

    private Poids(BigDecimal kilogrammes) {
        this.kilogrammes = kilogrammes;
    }

    public static Poids ofKilogrammes(BigDecimal kilogrammes) {
        if (kilogrammes == null) {
            throw new BusinessException("Le poids est obligatoire");
        }
        if (kilogrammes.signum() <= 0) {
            throw new BusinessException("Le poids doit être strictement positif");
        }
        if (kilogrammes.compareTo(MAX_KG) > 0) {
            throw new BusinessException("Le poids dépasse la borne plausible (" + MAX_KG + " kg)");
        }
        return new Poids(kilogrammes);
    }

    public BigDecimal kilogrammes() {
        return kilogrammes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Poids other)) return false;
        return kilogrammes.compareTo(other.kilogrammes) == 0;
    }

    @Override
    public int hashCode() {
        return kilogrammes.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return kilogrammes.toPlainString() + " kg";
    }
}

