package com.hemodialyse.backend.domain.shared.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;

/**
 * Value Object — a non-negative quantity (Shared Kernel, AGENTS.md §16).
 * <p>
 * Immutable; encapsulates its own validation to remove the "primitive obsession"
 * of passing raw {@link BigDecimal} around the stock domain. Arithmetic preserves
 * the underlying {@link BigDecimal} scale so existing serialized values are unchanged.
 */
public final class Quantite {

    private final BigDecimal value;

    private Quantite(BigDecimal value) {
        this.value = value;
    }

    public static Quantite of(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("La quantité est obligatoire");
        }
        if (value.signum() < 0) {
            throw new BusinessException("La quantité ne peut pas être négative");
        }
        return new Quantite(value);
    }

    public static Quantite zero() {
        return new Quantite(BigDecimal.ZERO);
    }

    public BigDecimal value() {
        return value;
    }

    public Quantite add(Quantite other) {
        return of(this.value.add(other.value));
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantite other)) return false;
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}

