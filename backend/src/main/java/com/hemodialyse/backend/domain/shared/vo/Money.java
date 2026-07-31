package com.hemodialyse.backend.domain.shared.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;

/**
 * Value Object — a non-negative monetary amount (Shared Kernel, AGENTS.md §16).
 * <p>
 * Immutable; removes the "primitive obsession" of passing raw {@link BigDecimal}
 * amounts around the stock/billing domains. Arithmetic preserves the underlying
 * {@link BigDecimal} scale, so existing computed/serialized values are unchanged.
 */
public final class Money {

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException("Le montant est obligatoire");
        }
        if (amount.signum() < 0) {
            throw new BusinessException("Le montant ne peut pas être négatif");
        }
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return of(this.amount.add(other.amount));
    }

    /**
     * Total for {@code this} unit price applied to a {@link Quantite}.
     */
    public Money times(Quantite quantite) {
        return of(this.amount.multiply(quantite.value()));
    }

    public BigDecimal amount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}

